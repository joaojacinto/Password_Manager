package crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

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
}