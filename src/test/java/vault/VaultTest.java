package vault;

import static org.junit.jupiter.api.Assertions.*;
import crypto.CryptoService;
import crypto.DecryptionException;
import org.junit.jupiter.api.Test;

class VaultTest {

    @Test
    void addAndFindEntry() {
        Vault vault = new Vault();
        vault.add(new VaultEntry("example.com", "joao", "pw"));

        assertTrue(vault.find("EXAMPLE.com", "joao").isPresent());
    }

    @Test
    void duplicateEntryIsRejected() {
        Vault vault = new Vault();
        vault.add(new VaultEntry("example.com", "joao", "pw"));

        assertThrows(IllegalArgumentException.class,
                () -> vault.add(new VaultEntry("example.com", "joao", "other")));
    }

    @Test
    void removeDeletesTheEntry() {
        Vault vault = new Vault();
        vault.add(new VaultEntry("example.com", "joao", "pw"));

        assertTrue(vault.remove("example.com", "joao"));
        assertTrue(vault.getEntries().isEmpty());
    }

    @Test
    void toStringDoesNotLeakThePassword() {
        VaultEntry entry = new VaultEntry("example.com", "joao", "hunter2");

        assertFalse(entry.toString().contains("hunter2"));
    }

    @Test
    void entryWithoutSiteOrPasswordIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new VaultEntry("", "joao", "pw"));
        assertThrows(IllegalArgumentException.class, () -> new VaultEntry("example.com", "joao", ""));
    }
}