package crypto;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public class CryptoService {
    // how many bytes, iterations and lenght SHA256
    private static final int SALT_LENGTH = 16;        // bytes
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;   // AES-256

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
}
