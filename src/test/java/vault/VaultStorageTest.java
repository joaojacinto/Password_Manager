package vault;

import static org.junit.jupiter.api.Assertions.*;
import crypto.CryptoService;
import crypto.DecryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

class VaultStorageTest {

    @TempDir
    Path dir;

    private VaultStorage storage;
    private Path file;

    @BeforeEach
    void setUp() {
        storage = new VaultStorage(new CryptoService());
        file = dir.resolve("vault.dat");
    }

    private VaultStorage freshStorage() {
        return new VaultStorage(new CryptoService());
    }

    private static VaultEntry entry(String name, String username, String password) {
        return VaultEntry.create(name, username, password, List.of("example.com"), "my notes");
    }

    @Test
    void entriesSurviveSaveAndUnlock() throws Exception {
        storage.create(file, "master".toCharArray());
        Vault vault = new Vault();
        VaultEntry original = entry("Example", "joao", "hunter2");
        vault.add(original);
        storage.save(vault);

        Vault loaded = freshStorage().unlock(file, "master".toCharArray());

        assertEquals(1, loaded.getEntries().size());
        assertEquals(original, loaded.getEntries().get(0));   // every field, including the dates
    }

    @Test
    void wrongMasterPasswordIsRejected() throws Exception {
        storage.create(file, "master".toCharArray());

        assertThrows(DecryptionException.class,
                () -> freshStorage().unlock(file, "wrong".toCharArray()));
    }

    @Test
    void fileDoesNotContainPlaintext() throws Exception {
        storage.create(file, "master".toCharArray());
        Vault vault = new Vault();
        vault.add(entry("Example", "joao", "hunter2"));
        storage.save(vault);

        String raw = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);

        assertFalse(raw.contains("hunter2"));
        assertFalse(raw.contains("Example"));
        assertFalse(raw.contains("joao"));
        assertFalse(raw.contains("my notes"));
        assertFalse(raw.contains("example.com"));
    }

    @Test
    void tamperedFileIsRejected() throws Exception {
        storage.create(file, "master".toCharArray());
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);

        assertThrows(DecryptionException.class,
                () -> freshStorage().unlock(file, "master".toCharArray()));
    }

    @Test
    void savingTheSameVaultTwiceProducesDifferentFiles() throws Exception {
        storage.create(file, "master".toCharArray());
        byte[] first = Files.readAllBytes(file);

        storage.save(new Vault());
        byte[] second = Files.readAllBytes(file);

        assertFalse(Arrays.equals(first, second));   // new IV each time
    }

    @Test
    void freshVaultHasExpectedFileSize() throws Exception {
        storage.create(file, "master".toCharArray());

        // version (1) + salt (16) + IV (12) + "[]" (2) + tag (16)
        assertEquals(47, Files.size(file));
    }

    @Test
    void noTempFileIsLeftBehind() throws Exception {
        storage.create(file, "master".toCharArray());
        storage.save(new Vault());

        assertFalse(Files.exists(dir.resolve("vault.dat.tmp")));
    }

    @Test
    void cannotCreateOverAnExistingVault() throws Exception {
        storage.create(file, "master".toCharArray());

        assertThrows(IOException.class,
                () -> freshStorage().create(file, "other".toCharArray()));
    }

    @Test
    void truncatedFileIsRejected() throws Exception {
        Files.write(file, new byte[5]);

        assertThrows(IOException.class,
                () -> storage.unlock(file, "master".toCharArray()));
    }

    @Test
    void saveWhileLockedFails() {
        assertThrows(IllegalStateException.class, () -> storage.save(new Vault()));
    }

    @Test
    void saveAfterLockFails() throws Exception {
        storage.create(file, "master".toCharArray());
        storage.lock();

        assertThrows(IllegalStateException.class, () -> storage.save(new Vault()));
    }

    @Test
    void failedUnlockLeavesStorageLocked() throws Exception {
        storage.create(file, "master".toCharArray());
        VaultStorage other = freshStorage();

        assertThrows(DecryptionException.class, () -> other.unlock(file, "wrong".toCharArray()));
        assertThrows(IllegalStateException.class, () -> other.save(new Vault()));
    }

    @Test
    void savedVaultUsesTheCurrentFormatVersion() throws Exception {
        storage.create(file, "master".toCharArray());

        assertEquals(2, Files.readAllBytes(file)[0]);
    }

    @Test
    void unknownVersionIsRejected() throws Exception {
        storage.create(file, "master".toCharArray());
        byte[] bytes = Files.readAllBytes(file);
        bytes[0] = 99;
        Files.write(file, bytes);

        assertThrows(IOException.class,
                () -> freshStorage().unlock(file, "master".toCharArray()));
    }

    @Test
    void version1VaultIsMigratedAndUpgradedOnSave() throws Exception {
        // build a version 1 file by hand, exactly as the old code wrote it
        CryptoService crypto = new CryptoService();
        byte[] salt = crypto.generateSalt();
        byte[] json = "[{\"site\":\"example.com\",\"username\":\"joao\",\"password\":\"hunter2\"}]"
                .getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = crypto.encrypt(crypto.deriveKey("master".toCharArray(), salt), json);

        byte[] legacyFile = new byte[1 + salt.length + encrypted.length];
        legacyFile[0] = 1;
        System.arraycopy(salt, 0, legacyFile, 1, salt.length);
        System.arraycopy(encrypted, 0, legacyFile, 1 + salt.length, encrypted.length);
        Files.write(file, legacyFile);

        Vault vault = storage.unlock(file, "master".toCharArray());

        VaultEntry migrated = vault.getEntries().get(0);
        assertEquals("example.com", migrated.name());
        assertEquals("joao", migrated.username());
        assertEquals("hunter2", migrated.password());
        assertTrue(migrated.websites().isEmpty());

        storage.save(vault);   // saving writes the new format

        assertEquals(2, Files.readAllBytes(file)[0]);
        Vault reloaded = freshStorage().unlock(file, "master".toCharArray());
        assertEquals("hunter2", reloaded.getEntries().get(0).password());
    }
}