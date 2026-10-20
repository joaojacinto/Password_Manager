package vault;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultEntryTest {

    private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");

    private static VaultEntry sample() {
        return new VaultEntry("Gmail", "joao", "pw", List.of("gmail.com"), "note", OLD, OLD);
    }

    @Test
    void nameAndPasswordAreRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> VaultEntry.create("", "joao", "pw", List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> VaultEntry.create("Gmail", "joao", "", List.of(), ""));
    }

    @Test
    void optionalFieldsDefaultToEmpty() {
        VaultEntry entry = VaultEntry.create("Gmail", null, "pw", null, null);

        assertEquals("", entry.username());
        assertEquals("", entry.notes());
        assertTrue(entry.websites().isEmpty());
    }

    @Test
    void websitesAreTrimmedAndBlanksRemoved() {
        VaultEntry entry = VaultEntry.create("Gmail", "", "pw", List.of(" gmail.com ", "", "  "), "");

        assertEquals(List.of("gmail.com"), entry.websites());
    }

    @Test
    void newEntryHasEqualCreationAndModificationDates() {
        VaultEntry entry = VaultEntry.create("Gmail", "joao", "pw", List.of(), "");

        assertEquals(entry.createdAt(), entry.modifiedAt());
    }

    @Test
    void changingAnEntryKeepsCreationDateAndUpdatesModificationDate() {
        VaultEntry changed = sample().withChanges("Gmail", "joao", "newpw", List.of("gmail.com"), "note");

        assertEquals(OLD, changed.createdAt());
        assertTrue(changed.modifiedAt().isAfter(OLD));
        assertEquals("newpw", changed.password());
    }

    @Test
    void savingWithoutChangesReturnsTheSameEntry() {
        VaultEntry entry = sample();

        assertSame(entry, entry.withChanges("Gmail", "joao", "pw", List.of("gmail.com"), "note"));
    }

    @Test
    void toStringDoesNotLeakPasswordOrNotes() {
        VaultEntry entry = VaultEntry.create("Gmail", "joao", "hunter2", List.of(), "secret note");

        assertFalse(entry.toString().contains("hunter2"));
        assertFalse(entry.toString().contains("secret note"));
    }
}