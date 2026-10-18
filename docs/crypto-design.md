# Cryptography design

This document explains how the password manager protects the vault, and why each decision was made.
It's a learning project: the design follows common recommendations, but it hasn't been audited.

Implemented in `CryptoService` (`deriveKey`, `encrypt`, `decrypt`).

## The problem

The vault contains passwords, so it must never be stored in readable form.
The only secret the user remembers is the **master password**, and the vault must be unlockable
with that password alone, without storing the password or the encryption key anywhere.

## Overview

```
master password + salt  →  PBKDF2 (slow)  →  256-bit key
key + IV + vault data   →  AES-256-GCM    →  ciphertext + authentication tag
```

The key is never saved. Every time the vault is opened, it is derived again from what the user types
plus the salt stored in the file.

## Why the master password isn't used directly as the key

- **Wrong shape**: AES-256 needs exactly 32 bytes. A password has arbitrary length.
- **Low randomness**: human-chosen passwords cover a tiny fraction of all possible 32-byte values,
  so they can be guessed far faster than a random key.
- **Reuse**: the same password would produce the same key in every vault.

A key derivation function (KDF) solves these problems.

## Key derivation: PBKDF2

The project uses `PBKDF2WithHmacSHA256`, which ships with the JDK (no external library).

### The salt

- 16 random bytes from `SecureRandom`, generated once when the vault is created.
- Stored in the vault file **in plain form**. It is not a secret.
- The same master password produces a different key for every vault, and an attacker can't reuse
  precomputed tables of "password → key" because each vault has its own salt.

### The iterations

- PBKDF2 repeats its hashing step 600,000 times on purpose.
- Unlocking costs the user a fraction of a second.
- An attacker guessing passwords pays that cost for every single guess, which makes brute force slow.
- 600,000 is the figure OWASP recommended for PBKDF2-HMAC-SHA256 when this was written. Recommendations
  change, so the value should be reviewed periodically.

### Determinism

The same password and salt always produce the same key. This is what makes unlocking work:
the key is re-derived on demand instead of being stored.

## Encryption: AES-256-GCM

The vault is encrypted with AES-256 in GCM mode (`AES/GCM/NoPadding`), using the key derived above.
`CryptoService.encrypt` and `CryptoService.decrypt` implement it with the JDK's own `javax.crypto`
classes; no custom cryptography is written.

### Why GCM

AES on its own only transforms fixed-size blocks, so a *mode* is needed to handle longer data.
GCM provides two guarantees in one step:

- **Confidentiality**: the data can't be read without the key.
- **Integrity**: the data can't be modified without being detected.

Older modes such as CBC only provide confidentiality, so a separate integrity check (an HMAC)
would be needed on top. With GCM, decryption either returns exactly the original data or fails.

### The IV (initialization vector)

- 12 random bytes from `SecureRandom`, generated for **every** encryption.
- Stored in front of the ciphertext in plain form. It is not a secret.
- It makes the same data encrypt to a different result each time, so an attacker can't tell whether
  two entries are equal, or whether the vault changed between two saves, by comparing the output.
- **An IV must never be reused with the same key.** Reuse breaks GCM's guarantees (it can leak
  information about the data and allow forged messages). For that reason `encrypt` creates the IV
  internally and the caller can't choose it. With random 12-byte IVs, an accidental repeat is
  negligible for the number of times a vault is saved (the usual limit is around 2^32 encryptions per key).

### The authentication tag

- 16 bytes computed by GCM from the key and the data, appended to the ciphertext.
- Checked on decryption. If the data was modified, or the key is wrong, the check fails and
  **no plaintext is released**.
- This is also how a wrong master password is detected: a different password derives a different key,
  and the tag doesn't match. Because of this, no password hash needs to be stored for verification.
- A wrong password and a corrupted file look identical on purpose, so `decrypt` reports both with
  the same `DecryptionException`.

### Output layout

```
[ IV (12 bytes) ][ ciphertext ][ tag (16 bytes) ]
```

`encrypt` returns this single array and `decrypt` expects it.

### Salt vs. IV

| | Salt | IV |
|---|---|---|
| Used in | key derivation (PBKDF2) | encryption (AES-GCM) |
| Changes | never during the life of a vault | on every save |
| Length | 16 bytes | 12 bytes |
| Secret? | no | no |

## What is stored and what is not

| Item | Stored on disk? |
|---|---|
| Master password | **Never** |
| Derived key | **Never**, only held in memory while the vault is open |
| Salt | Yes, in plain form |
| IV | Yes, in plain form, in front of the ciphertext |
| Authentication tag | Yes, at the end of the ciphertext |
| Vault contents | Only encrypted |

## Memory hygiene

- The master password is handled as a `char[]`, not a `String`. Strings are immutable and can't be
  erased, while an array can be overwritten with zeros once it's no longer needed.
- The temporary key bytes and the `PBEKeySpec` password copy are cleared after use.
- This reduces how long secrets stay in memory, but Java gives no hard guarantees
  (the garbage collector may have copied data before it was wiped).

## Parameters

| Parameter | Value |
|---|---|
| Algorithm | PBKDF2WithHmacSHA256 |
| Iterations | 600,000 |
| Salt length | 16 bytes |
| Key length | 256 bits (AES-256) |
| Cipher | AES/GCM/NoPadding |
| IV length | 12 bytes |
| Tag length | 128 bits (16 bytes) |

## Limitations

- A weak master password is still the weakest link: the KDF slows guessing, it doesn't stop it.
- A wrong master password and a corrupted or tampered file are indistinguishable: both fail the
  authentication check.
- No audit has been done. Do not use this to protect real credentials.