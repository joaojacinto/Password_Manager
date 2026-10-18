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

    // normal getter
    public List<VaultEntry> getEntries() {
        return List.copyOf(entries);
    }

    public void add(VaultEntry entry) {
        if (find(entry.site(), entry.username()).isPresent()) {
            throw new IllegalArgumentException("An entry for this site and username already exists.");
        }
        entries.add(entry);
    }

    public boolean remove(String site, String username) {
        return entries.removeIf(e -> e.site().equalsIgnoreCase(site)
                && e.username().equalsIgnoreCase(username));
    }

    public Optional<VaultEntry> find(String site, String username) {
        return entries.stream()
                .filter(e -> e.site().equalsIgnoreCase(site)
                        && e.username().equalsIgnoreCase(username))
                .findFirst();
    }

    public void replace(VaultEntry oldEntry, VaultEntry newEntry) {
        int index = entries.indexOf(oldEntry);
        if (index < 0) {
            throw new IllegalArgumentException("Entry not found.");
        }

        boolean sameKey = oldEntry.site().equalsIgnoreCase(newEntry.site())
                && oldEntry.username().equalsIgnoreCase(newEntry.username());
        if (!sameKey && find(newEntry.site(), newEntry.username()).isPresent()) {
            throw new IllegalArgumentException("An entry for this site and username already exists.");
        }

        entries.set(index, newEntry);
    }
}