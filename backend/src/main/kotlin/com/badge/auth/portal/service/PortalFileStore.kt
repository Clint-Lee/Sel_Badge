package com.badge.auth.portal.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest

/** 单实例门户文件存储：原子替换、账号散列路径，损坏时保留原文件并明确失败。 */
@Component
class PortalFileStore(
    val mapper: ObjectMapper,
    @Value("\${badge.portal.data-dir:./data/portal}") directory: String
) {
    private val root: Path = Path.of(directory).toAbsolutePath().normalize()

    init { Files.createDirectories(root) }

    fun <T> read(name: String, type: Class<T>, default: () -> T): T {
        val path = root.resolve(name)
        return if (Files.exists(path)) mapper.readValue(path.toFile(), type) else default()
    }

    fun write(name: String, value: Any) {
        val target = root.resolve(name)
        val temp = Files.createTempFile(root, "portal-", ".tmp")
        try {
            mapper.writeValue(temp.toFile(), value)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temp) }
    }

    fun preferenceFile(username: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).joinToString("") { "%02x".format(it) }
        return "preferences-$hash.json"
    }
}
