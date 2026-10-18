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

    @Test
    void entriesSurviveSaveAndUnlock() throws Exception {
        storage.create(file, "master".toCharArray());
        Vault vault = new Vault();
        vault.add(new VaultEntry("example.com", "joao", "hunter2"));
        storage.save(vault);

        Vault loaded = freshStorage().unlock(file, "master".toCharArray());

        assertEquals(1, loaded.getEntries().size());
        assertEquals("hunter2", loaded.getEntries().get(0).password());
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
        vault.add(new VaultEntry("example.com", "joao", "hunter2"));
        storage.save(vault);

        String raw = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);

        assertFalse(raw.contains("hunter2"));
        assertFalse(raw.contains("example.com"));
        assertFalse(raw.contains("joao"));
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
}