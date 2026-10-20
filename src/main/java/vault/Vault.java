package vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Vault {

    private final List<VaultEntry> entries;

    public Vault() {
        this.entries = new ArrayList<>();
    }

    public Vault(List<VaultEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public List<VaultEntry> getEntries() {
        return List.copyOf(entries);
    }

    public void add(VaultEntry entry) {
        if (find(entry.name(), entry.username()).isPresent()) {
            throw new IllegalArgumentException("An entry with this name and username already exists.");
        }
        entries.add(entry);
    }

    public boolean remove(String name, String username) {
        return entries.removeIf(e -> sameAccount(e, name, username));
    }

    public Optional<VaultEntry> find(String name, String username) {
        return entries.stream()
                .filter(e -> sameAccount(e, name, username))
                .findFirst();
    }

    public void replace(VaultEntry oldEntry, VaultEntry newEntry) {
        int index = entries.indexOf(oldEntry);
        if (index < 0) {
            throw new IllegalArgumentException("Entry not found.");
        }

        boolean sameKey = sameAccount(oldEntry, newEntry.name(), newEntry.username());
        if (!sameKey && find(newEntry.name(), newEntry.username()).isPresent()) {
            throw new IllegalArgumentException("An entry with this name and username already exists.");
        }

        entries.set(index, newEntry);
    }

    private static boolean sameAccount(VaultEntry entry, String name, String username) {
        return entry.name().equalsIgnoreCase(name)
                && entry.username().equalsIgnoreCase(username);
    }
}