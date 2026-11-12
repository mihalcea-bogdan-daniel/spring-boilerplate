-- Encrypted values (AES-GCM, "v1:" prefix) are longer than the raw base32 secret.
-- Existing plaintext secrets keep working and are encrypted on the next write.
alter table users alter column totp_secret type varchar(255);
