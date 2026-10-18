package crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private CryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new CryptoService();
    }

    @Test
    void saltHasExpectedLength() {
        assertEquals(16, crypto.generateSalt().length);
    }

    @Test
    void twoSaltsAreDifferent() {
        assertFalse(java.util.Arrays.equals(crypto.generateSalt(), crypto.generateSalt()));
    }

    @Test
    void keyIs256BitAes() {
        SecretKey key = crypto.deriveKey("master".toCharArray(), crypto.generateSalt());

        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    void samePasswordAndSaltGiveSameKey() {
        byte[] salt = crypto.generateSalt();

        SecretKey k1 = crypto.deriveKey("master".toCharArray(), salt);
        SecretKey k2 = crypto.deriveKey("master".toCharArray(), salt);

        assertArrayEquals(k1.getEncoded(), k2.getEncoded());
    }

    @Test
    void differentSaltGivesDifferentKey() {
        SecretKey k1 = crypto.deriveKey("master".toCharArray(), crypto.generateSalt());
        SecretKey k2 = crypto.deriveKey("master".toCharArray(), crypto.generateSalt());

        assertFalse(java.util.Arrays.equals(k1.getEncoded(), k2.getEncoded()));
    }

    @Test
    void differentPasswordGivesDifferentKey() {
        byte[] salt = crypto.generateSalt();

        SecretKey k1 = crypto.deriveKey("master".toCharArray(), salt);
        SecretKey k2 = crypto.deriveKey("Master".toCharArray(), salt);

        assertFalse(java.util.Arrays.equals(k1.getEncoded(), k2.getEncoded()));
    }

    private SecretKey randomKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test
    void decryptReturnsTheOriginalData() throws Exception {
        SecretKey key = randomKey();
        byte[] original = "site: example.com / pass: hunter2".getBytes(StandardCharsets.UTF_8);

        byte[] decrypted = crypto.decrypt(key, crypto.encrypt(key, original));

        assertArrayEquals(original, decrypted);
    }

    @Test
    void sameDataEncryptsDifferentlyEachTime() throws Exception {
        SecretKey key = randomKey();
        byte[] data = "same text".getBytes(StandardCharsets.UTF_8);

        assertFalse(Arrays.equals(crypto.encrypt(key, data), crypto.encrypt(key, data)));
    }

    @Test
    void outputIsIvPlusCiphertextPlusTag() throws Exception {
        byte[] data = new byte[100];

        byte[] encrypted = crypto.encrypt(randomKey(), data);

        assertEquals(12 + 100 + 16, encrypted.length);
    }

    @Test
    void wrongKeyFailsToDecrypt() throws Exception {
        byte[] encrypted = crypto.encrypt(randomKey(), "secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(DecryptionException.class, () -> crypto.decrypt(randomKey(), encrypted));
    }

    @Test
    void tamperedDataFailsToDecrypt() throws Exception {
        SecretKey key = randomKey();
        byte[] encrypted = crypto.encrypt(key, "secret".getBytes(StandardCharsets.UTF_8));

        encrypted[encrypted.length - 1] ^= 1;   // flip one bit in the tag

        assertThrows(DecryptionException.class, () -> crypto.decrypt(key, encrypted));
    }

    @Test
    void tooShortDataIsRejected() throws Exception {
        assertThrows(DecryptionException.class, () -> crypto.decrypt(randomKey(), new byte[5]));
    }

    @Test
    void fullUnlockFlowWorksWithMasterPassword() {
        byte[] salt = crypto.generateSalt();
        byte[] data = "vault contents".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = crypto.encrypt(crypto.deriveKey("master".toCharArray(), salt), data);
        // later: the key is derived again from what the user types plus the stored salt
        byte[] decrypted = crypto.decrypt(crypto.deriveKey("master".toCharArray(), salt), encrypted);

        assertArrayEquals(data, decrypted);
    }
}