package app;

import crypto.CryptoService;
import crypto.DecryptionException;
import generator.PasswordGenerator;
import vault.Vault;
import vault.VaultEntry;
import vault.VaultStorage;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    private static final Path VAULT_FILE = Path.of("vault.dat");
    private static final int MIN_MASTER_LENGTH = 12;
    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_PASSWORD_LENGTH = 20;

    private final Scanner scanner = new Scanner(System.in);
    private final VaultStorage storage = new VaultStorage(new CryptoService());
    private final PasswordGenerator generator = new PasswordGenerator();
    private Vault vault;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        try {
            vault = Files.exists(VAULT_FILE) ? unlockExisting() : createNew();
            if (vault != null) {
                menuLoop();
            }
        } catch (IOException e) {
            System.out.println("File error: " + e.getMessage());
        } finally {
            storage.lock();
        }
    }

    // ---------- opening the vault ----------

    private Vault createNew() throws IOException {
        System.out.println("No vault found. Let's create one.");
        System.out.println("Choose a master password (at least " + MIN_MASTER_LENGTH + " characters).");
        System.out.println("If you forget it, the vault cannot be recovered.");

        while (true) {
            char[] first = readSecret("Master password: ");
            char[] second = readSecret("Repeat it: ");
            try {
                if (first.length < MIN_MASTER_LENGTH) {
                    System.out.println("Too short.");
                } else if (!Arrays.equals(first, second)) {
                    System.out.println("The passwords don't match.");
                } else {
                    storage.create(VAULT_FILE, first);
                    System.out.println("Vault created.");
                    return new Vault();
                }
            } finally {
                Arrays.fill(first, '\0');
                Arrays.fill(second, '\0');
            }
        }
    }

    private Vault unlockExisting() throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            char[] password = readSecret("Master password: ");
            try {
                Vault unlocked = storage.unlock(VAULT_FILE, password);
                System.out.println("Vault unlocked.");
                return unlocked;
            } catch (DecryptionException e) {
                System.out.println("Wrong password, or the file is corrupted. ("
                        + attempt + "/" + MAX_ATTEMPTS + ")");
            } finally {
                Arrays.fill(password, '\0');
            }
        }
        System.out.println("Too many attempts.");
        return null;
    }

    // ---------- menu ----------

    private void menuLoop() throws IOException {
        while (true) {
            System.out.println();
            System.out.println("1) List entries");
            System.out.println("2) Add entry");
            System.out.println("3) Show a password");
            System.out.println("4) Delete entry");
            System.out.println("5) Generate a password");
            System.out.println("0) Exit");
            System.out.print("> ");

            switch (scanner.nextLine().trim()) {
                case "1" -> listEntries();
                case "2" -> addEntry();
                case "3" -> showPassword();
                case "4" -> deleteEntry();
                case "5" -> generateOnly();
                case "0" -> {
                    System.out.println("Bye.");
                    return;
                }
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private void listEntries() {
        List<VaultEntry> entries = vault.getEntries();
        if (entries.isEmpty()) {
            System.out.println("The vault is empty.");
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            VaultEntry e = entries.get(i);
            System.out.println((i + 1) + ". " + e.site() + "  (" + e.username() + ")");
        }
    }

    private void addEntry() throws IOException {
        String site = ask("Site: ");
        String username = ask("Username: ");
        boolean generate = ask("Generate a password? (y/n): ").equalsIgnoreCase("y");

        try {
            String password = generate ? generatePassword() : typePassword();
            vault.add(new VaultEntry(site, username, password));
            storage.save(vault);
            System.out.println("Saved.");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    private void showPassword() {
        String site = ask("Site: ");
        String username = ask("Username: ");

        Optional<VaultEntry> entry = vault.find(site, username);
        if (entry.isPresent()) {
            System.out.println("Password: " + entry.get().password());
        } else {
            System.out.println("No such entry.");
        }
    }

    private void deleteEntry() throws IOException {
        String site = ask("Site: ");
        String username = ask("Username: ");

        if (vault.find(site, username).isEmpty()) {
            System.out.println("No such entry.");
            return;
        }
        if (!ask("Delete it? (y/n): ").equalsIgnoreCase("y")) {
            System.out.println("Cancelled.");
            return;
        }
        vault.remove(site, username);
        storage.save(vault);
        System.out.println("Deleted.");
    }

    private void generateOnly() {
        try {
            System.out.println("Generated: " + generatePassword());
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }

    // ---------- helpers ----------

    private String generatePassword() {
        String text = ask("Length (Enter for " + DEFAULT_PASSWORD_LENGTH + "): ");
        int length = DEFAULT_PASSWORD_LENGTH;
        if (!text.isBlank()) {
            try {
                length = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Length must be a number.");
            }
        }
        return generator.generate(length, true, true, true, true);
    }

    private String typePassword() {
        char[] typed = readSecret("Password: ");
        String password = new String(typed);
        Arrays.fill(typed, '\0');
        return password;
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private char[] readSecret(String prompt) {
        Console console = System.console();
        if (console != null && console.isTerminal()) {
            return console.readPassword(prompt);
        }
        // no real terminal (for example the IntelliJ Run window): the text will be visible
        System.out.print(prompt);
        return scanner.nextLine().toCharArray();
    }
}