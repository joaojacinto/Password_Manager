package vault;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record VaultEntry(
        String name,
        String username,
        String password,
        List<String> websites,
        String notes,
        Instant createdAt,
        Instant modifiedAt) {

    public VaultEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (createdAt == null || modifiedAt == null) {
            throw new IllegalArgumentException("Dates are required.");
        }
        name = name.trim();
        username = username == null ? "" : username.trim();
        notes = notes == null ? "" : notes;
        websites = websites == null ? List.of() : websites.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(w -> !w.isEmpty())
                .toList();
    }

    // new entry with NOW dates
    public static VaultEntry create(String name, String username, String password,
                                    List<String> websites, String notes) {
        Instant now = Instant.now();
        return new VaultEntry(name, username, password, websites, notes, now, now);
    }

    /**
     * Returns the edited version of this entry: same creation date, new modification date.
     * If nothing actually changed, returns this same entry so the modification date doesn't move.
     */
    public VaultEntry withChanges(String name, String username, String password,
                                  List<String> websites, String notes) {
        VaultEntry changed = new VaultEntry(name, username, password, websites, notes, createdAt, Instant.now());
        return sameContent(changed) ? this : changed;
    }

    private boolean sameContent(VaultEntry other) {
        return name.equals(other.name)
                && username.equals(other.username)
                && password.equals(other.password)
                && websites.equals(other.websites)
                && notes.equals(other.notes);
    }

    // never prints the password or the notes, so they can't end up in logs
    @Override
    public String toString() {
        return "VaultEntry[name=" + name + "]";
    }
}