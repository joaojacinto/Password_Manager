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

    @Test
    void replaceChangesTheEntry() {
        Vault vault = new Vault();
        VaultEntry old = new VaultEntry("example.com", "joao", "pw");
        vault.add(old);

        vault.replace(old, new VaultEntry("example.com", "joao", "newpw"));

        assertEquals(1, vault.getEntries().size());
        assertEquals("newpw", vault.find("example.com", "joao").get().password());
    }

    @Test
    void replaceCannotCreateADuplicate() {
        Vault vault = new Vault();
        VaultEntry first = new VaultEntry("example.com", "joao", "pw");
        VaultEntry second = new VaultEntry("example.com", "maria", "pw");
        vault.add(first);
        vault.add(second);

        assertThrows(IllegalArgumentException.class,
                () -> vault.replace(second, new VaultEntry("example.com", "joao", "x")));
    }

    @Test
    void replaceUnknownEntryFails() {
        Vault vault = new Vault();

        assertThrows(IllegalArgumentException.class,
                () -> vault.replace(new VaultEntry("a.com", "x", "pw"), new VaultEntry("b.com", "x", "pw")));
    }
}