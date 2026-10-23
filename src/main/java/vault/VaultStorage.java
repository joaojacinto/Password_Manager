package vault;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import crypto.CryptoService;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class VaultStorage {

    private static final byte FORMAT_VERSION = 2;
    private static final byte LEGACY_FORMAT_VERSION = 1;
    // HEADER_LENGTH is 17: one version byte plus the 16-byte salt
    private static final int HEADER_LENGTH = 1 + CryptoService.SALT_LENGTH;

    private final CryptoService crypto;
    // mapper is Jackson’s converter between java objects and JSON.
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // path, salt and key are the state of the unlocked vault. while the vault is locked, they’re all null.
    private Path path;
    private byte[] salt;
    private SecretKey key;

    // constructor for crypto
    public VaultStorage(CryptoService crypto) {
        this.crypto = crypto;
    }

    //
    public void create(Path path, char[] masterPassword) throws IOException {
        if (Files.exists(path)) {
            throw new IOException("A vault already exists at " + path);
        }
        this.path = path;
        this.salt = crypto.generateSalt();
        this.key = crypto.deriveKey(masterPassword, salt);
        save(new Vault());
    }

    public Vault unlock(Path path, char[] masterPassword) throws IOException {
        byte[] file = Files.readAllBytes(path);

        if (file.length <= HEADER_LENGTH) {
            throw new IOException("Not a valid vault file.");
        }

        byte version = file[0];
        if (version != FORMAT_VERSION && version != LEGACY_FORMAT_VERSION) {
            throw new IOException("Unsupported vault version: " + version);
        }

        byte[] fileSalt = Arrays.copyOfRange(file, 1, HEADER_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(file, HEADER_LENGTH, file.length);

        SecretKey fileKey = crypto.deriveKey(masterPassword, fileSalt);
        byte[] json = crypto.decrypt(fileKey, encrypted);   // DecryptionException if wrong password
        try {
            List<VaultEntry> entries = version == LEGACY_FORMAT_VERSION
                    ? readLegacy(json)
                    : mapper.readValue(json, new TypeReference<List<VaultEntry>>() {});
            // only remember the state once everything worked
            this.path = path;
            this.salt = fileSalt;
            this.key = fileKey;
            return new Vault(entries);
        } finally {
            Arrays.fill(json, (byte) 0);
        }
    }

    public void save(Vault vault) throws IOException {
        if (key == null) {
            throw new IllegalStateException("Vault is locked.");
        }

        byte[] json = mapper.writeValueAsBytes(vault.getEntries());
        byte[] encrypted;
        try {
            encrypted = crypto.encrypt(key, json);
        } finally {
            Arrays.fill(json, (byte) 0);
        }

        byte[] file = new byte[HEADER_LENGTH + encrypted.length];
        file[0] = FORMAT_VERSION;
        System.arraycopy(salt, 0, file, 1, CryptoService.SALT_LENGTH);
        System.arraycopy(encrypted, 0, file, HEADER_LENGTH, encrypted.length);

        // write to a temp file, then move it over the real one
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, file);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void lock() {
        path = null;
        salt = null;
        key = null;
    }

    // version 1 stored only site, username and password
    private List<VaultEntry> readLegacy(byte[] json) throws IOException {
        List<EntryV1> old = mapper.readValue(json, new TypeReference<List<EntryV1>>() {});
        Instant migrated = Instant.now();   // the original dates are unknown
        return old.stream()
                .map(e -> new VaultEntry(e.site(), e.username(), e.password(), List.of(), "", migrated, migrated))
                .toList();
    }

    record EntryV1(String site, String username, String password) {}
}
