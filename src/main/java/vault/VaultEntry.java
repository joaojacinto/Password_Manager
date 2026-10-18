package vault;

public record VaultEntry(String site, String username, String password) {

    public VaultEntry {
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("Site is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (username == null) {
            username = "";
        }
    }

    // default toString. never writes in logs
    @Override
    public String toString() {
        return "VaultEntry[site=" + site + ", username=" + username + "]";
    }
}