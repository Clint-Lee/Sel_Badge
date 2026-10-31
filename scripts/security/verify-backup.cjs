// Decrypt locally using the original PEM key; only print the verified checksum.
const fs = require('node:fs');
const crypto = require('node:crypto');
const [archive, privateKey] = process.argv.slice(2);
const metadata = JSON.parse(fs.readFileSync(archive + '.json', 'utf8'));
if (metadata.version !== 1) throw Error('Unsupported archive version');
const key = crypto.privateDecrypt({ key: fs.readFileSync(privateKey), oaepHash: 'sha256' }, Buffer.from(metadata.wrappedKey, 'hex'));
const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(metadata.nonce, 'hex'));
decipher.setAuthTag(Buffer.from(metadata.tag, 'hex'));
const hash = crypto.createHash('sha256');
const input = fs.createReadStream(archive);
input.on('data', chunk => hash.update(decipher.update(chunk)));
input.on('end', () => {
  hash.update(decipher.final());
  const actual = hash.digest('hex');
  if (actual !== metadata.plaintextSha256) throw Error('Backup hash mismatch');
  console.log('Encrypted backup restored in memory and SHA-256 verified: ' + actual);
});
input.on('error', error => { throw error; });
