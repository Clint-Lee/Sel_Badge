package com.badge.maintenance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Offline, transactional AES rotation. Credentials are read from protected files, never CLI values. */
public final class RotateSecrets {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RotateSecrets() { }

    private static Properties properties(Path path) throws Exception {
        Properties result = new Properties();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator > 0) result.setProperty(line.substring(0, separator), line.substring(separator + 1));
        }
        return result;
    }

    private static byte[] key(String phrase) throws Exception {
        if (phrase == null || phrase.length() < 32) throw new IllegalArgumentException("Missing protected AES key");
        return MessageDigest.getInstance("SHA-256").digest(phrase.getBytes(StandardCharsets.UTF_8));
    }

    private static String decrypt(String input, byte[] key) throws Exception {
        if (input == null || input.isEmpty()) return input;
        byte[] bytes = Base64.getDecoder().decode(input);
        if (bytes.length < IV_BYTES + TAG_BITS / Byte.SIZE) throw new IllegalStateException("Invalid encrypted field; migration aborted");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
        return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
    }

    private static String encrypt(String input, byte[] key) throws Exception {
        if (input == null || input.isEmpty()) return input;
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private static int rotate(Connection db, String table, String field, byte[] oldKey, byte[] newKey, Properties clients) throws Exception {
        int count = 0;
        String extra = table.equals("app_clients") ? ", client_id" : "";
        try (PreparedStatement select = db.prepareStatement("SELECT id, " + field + extra + " FROM " + table);
             ResultSet rows = select.executeQuery();
             PreparedStatement update = db.prepareStatement("UPDATE " + table + " SET " + field + "=? WHERE id=?")) {
            while (rows.next()) {
                String old = rows.getString(field);
                String plain = decrypt(old, oldKey);
                if (table.equals("app_clients")) plain = clients.getProperty(rows.getString("client_id"), plain);
                String next = encrypt(plain, newKey);
                if (plain != null && !plain.equals(decrypt(next, newKey))) throw new IllegalStateException("Round-trip validation failed");
                update.setString(1, next);
                update.setLong(2, rows.getLong("id"));
                update.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private static void publicUrl(Connection db, String client, String url, String callback) throws Exception {
        try (PreparedStatement statement = db.prepareStatement("UPDATE app_clients SET homepage_url=?, redirect_uri=? WHERE client_id=?")) {
            statement.setString(1, url);
            statement.setString(2, url + callback);
            statement.setString(3, client);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Missing registered application: " + client);
        }
    }

    private static int replaceDefaultPassword(Connection db, Properties next) throws Exception {
        Class<?> bcrypt = Class.forName("org.springframework.security.crypto.bcrypt.BCrypt");
        try (PreparedStatement select = db.prepareStatement("SELECT id, password_hash FROM users WHERE username='admin'");
             ResultSet rows = select.executeQuery()) {
            if (!rows.next()) return 0;
            boolean isDefault = (boolean) bcrypt.getMethod("checkpw", String.class, String.class)
                .invoke(null, "Admin@123456", rows.getString("password_hash"));
            if (!isDefault) return 0;
            String password = next.getProperty("RECOVERY_ADMIN_PASSWORD");
            if (password == null || password.length() < 24) throw new IllegalStateException("Missing protected recovery password");
            String salt = (String) bcrypt.getMethod("gensalt", int.class).invoke(null, 12);
            String hash = (String) bcrypt.getMethod("hashpw", String.class, String.class).invoke(null, password, salt);
            try (PreparedStatement update = db.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
                update.setString(1, hash);
                update.setLong(2, rows.getLong("id"));
                return update.executeUpdate();
            }
        }
    }

    /** Run only against a stopped database or an isolated backup copy. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Usage: jdbcUrl oldEnv newEnv clientSecretsFile");
        Properties old = properties(Path.of(args[1]));
        Properties next = properties(Path.of(args[2]));
        Properties clients = properties(Path.of(args[3]));
        byte[] oldKey = key(old.getProperty("BADGE_CRYPTO_MASTER_KEY"));
        byte[] newKey = key(next.getProperty("BADGE_CRYPTO_MASTER_KEY"));
        Class.forName("org.h2.Driver");
        try (Connection db = DriverManager.getConnection(args[0], "sa", "")) {
            db.setAutoCommit(false);
            try {
                Map<String, Integer> counts = new LinkedHashMap<>();
                counts.put("users", rotate(db, "users", "phone_encrypted", oldKey, newKey, clients));
                counts.put("app_clients", rotate(db, "app_clients", "client_secret_encrypted", oldKey, newKey, clients));
                counts.put("user_app_profiles", rotate(db, "user_app_profiles", "app_metadata_encrypted", oldKey, newKey, clients));
                counts.put("default_admin_passwords_replaced", replaceDefaultPassword(db, next));
                publicUrl(db, "app_portal_01", next.getProperty("PUBLIC_BADGE_URL"), "/sso/callback");
                publicUrl(db, "app_hotpot_video", next.getProperty("PUBLIC_MEDIA_URL"), "/auth/badge/callback");
                publicUrl(db, "app_xiyou_game", next.getProperty("PUBLIC_GAME_URL"), "/auth/badge/callback");
                try (PreparedStatement statement = db.prepareStatement("UPDATE app_clients SET status='INACTIVE' WHERE client_id IN ('app_game_arcade','app_cloud_service','app_wiki_knowledge')")) {
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = db.prepareStatement("DELETE FROM sso_tickets")) { statement.executeUpdate(); }
                db.commit();
                System.out.println("Encrypted-field rotation validated and committed: " + counts);
            } catch (Exception failure) {
                db.rollback();
                throw new IllegalStateException("Migration rolled back; no credential values logged", failure);
            }
        }
    }
}
