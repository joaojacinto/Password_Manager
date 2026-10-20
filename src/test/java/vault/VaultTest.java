package vault;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultTest {

    private static VaultEntry entry(String name, String username) {
        return VaultEntry.create(name, username, "pw", List.of(), "");
    }

    @Test
    void addAndFindEntry() {
        Vault vault = new Vault();
        vault.add(entry("Gmail", "joao"));

        assertTrue(vault.find("GMAIL", "joao").isPresent());
    }

    @Test
    void duplicateEntryIsRejected() {
        Vault vault = new Vault();
        vault.add(entry("Gmail", "joao"));

        assertThrows(IllegalArgumentException.class, () -> vault.add(entry("Gmail", "joao")));
    }

    @Test
    void sameNameWithDifferentUsernameIsAllowed() {
        Vault vault = new Vault();
        vault.add(entry("Gmail", "joao"));
        vault.add(entry("Gmail", "maria"));

        assertEquals(2, vault.getEntries().size());
    }

    @Test
    void removeDeletesTheEntry() {
        Vault vault = new Vault();
        vault.add(entry("Gmail", "joao"));

        assertTrue(vault.remove("Gmail", "joao"));
        assertTrue(vault.getEntries().isEmpty());
    }

    @Test
    void replaceChangesTheEntry() {
        Vault vault = new Vault();
        VaultEntry old = VaultEntry.create("Gmail", "joao", "pw", List.of(), "");
        vault.add(old);

        vault.replace(old, old.withChanges("Gmail", "joao", "newpw", List.of(), ""));

        assertEquals(1, vault.getEntries().size());
        assertEquals("newpw", vault.find("Gmail", "joao").get().password());
    }

    @Test
    void replaceCannotCreateADuplicate() {
        Vault vault = new Vault();
        VaultEntry first = entry("Gmail", "joao");
        VaultEntry second = entry("Gmail", "maria");
        vault.add(first);
        vault.add(second);

        assertThrows(IllegalArgumentException.class,
                () -> vault.replace(second, second.withChanges("Gmail", "joao", "pw", List.of(), "")));
    }

    @Test
    void replaceUnknownEntryFails() {
        Vault vault = new Vault();

        assertThrows(IllegalArgumentException.class,
                () -> vault.replace(entry("a", "x"), entry("b", "x")));
    }
}