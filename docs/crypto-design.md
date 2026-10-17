# Cryptography design

This document explains how the password manager protects the vault, and why each decision was made.
It's a learning project: the design follows common recommendations, but it hasn't been audited.

## The problem

The vault contains passwords, so it must never be stored in readable form.
The only secret the user remembers is the **master password**, and the vault must be unlockable
with that password alone, without storing the password or the encryption key anywhere.

## Overview

```
master password + salt  →  PBKDF2 (slow)  →  256-bit key  →  encrypts / decrypts the vault
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

## What is stored and what is not

| Item | Stored on disk? |
|---|---|
| Master password | **Never** |
| Derived key | **Never**, only held in memory while the vault is open |
| Salt | Yes, in plain form |
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

## Limitations

- A weak master password is still the weakest link: the KDF slows guessing, it doesn't stop it.
- No audit has been done. Do not use this to protect real credentials.