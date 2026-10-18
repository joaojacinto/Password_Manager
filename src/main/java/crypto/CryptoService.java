package crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public class CryptoService {
    // how many bytes, iterations and lenght SHA256
    public static final int SALT_LENGTH = 16;        // bytes
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;   // AES-256
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // bytes
    private static final int TAG_LENGTH_BITS = 128;   // 16 bytes

    // class that provides a cryptographically strong random number generator
    private final SecureRandom random = new SecureRandom();

    // a random value (16 bytes) generated once when the vault is created and stored in the file, in the open.
    // returns the array with bytes. it will produce a different key for every vault
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Derives a 256-bit AES key from the master password and a salt using PBKDF2.
     * The same password and salt always produce the same key; the key is never stored.
     * The caller is responsible for wiping the password array afterwards.
     */
    // take what the user typed on his password + the stored random salt and returns the 32 byte AES key
    // its public because the vault will call it
    public SecretKey deriveKey(char[] masterPassword, byte[] salt) {
        // constructor that takes a password, salt, iteration count, and to-be-derived key length for generating PBEKey of variable-key-size PBE ciphers. https://docs.oracle.com/javase/8/docs/api/javax/crypto/spec/PBEKeySpec.html
        PBEKeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            // a SecretKeyFactory is Java’s ready-made implementation of a key derivation algorithm.
            // java own standard implementation
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            // generateSecret(spec): this is where the 600,000 rounds run with some delay. It returns an object representing the derived key.
            // .getEncoded(): extracts the raw bytes. We now have 32 bytes in keyBytes.
            // claude generated
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            // SecretKeySpec labels keyBytes as “this is an AES key”
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            // after key has the copy, keybytes is a leftover so we fill it with 0's
            Arrays.fill(keyBytes, (byte) 0);
            return key;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key derivation failed", e);
        } finally {
            // PBEKeySpec keeps its own copy of the password, and clearPassword() erases it. This is why spec had to be declared outside the try.
            spec.clearPassword();
        }
    }

    public byte[] encrypt(SecretKey key, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);                         // a new IV for every encryption

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);   // ciphertext + tag

            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;                                   // [IV][ciphertext][tag]
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(SecretKey key, byte[] data) {
        if (data.length < IV_LENGTH + TAG_LENGTH_BITS / 8) {
            throw new DecryptionException("Encrypted data is too short.", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH));
            return cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        } catch (AEADBadTagException e) {
            throw new DecryptionException("Wrong password or corrupted data.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }


    

}
