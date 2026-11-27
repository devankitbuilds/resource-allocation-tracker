package com.rat.tracker.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true)
public class LocalStorageService implements FileStorageService {

    private final Path storageRoot;

    public LocalStorageService(
            @Value("${app.storage.local-path:./uploads}") String localPath) {
        this.storageRoot = Path.of(localPath).toAbsolutePath().normalize();
    }

    @Override
    public String save(InputStream content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("File content must not be null");
        }

        YearMonth currentMonth = YearMonth.now();
        String year = Integer.toString(currentMonth.getYear());
        String month = String.format(Locale.ROOT, "%02d", currentMonth.getMonthValue());
        String storageKey = year + "/" + month + "/" + UUID.randomUUID();

        Path destination = resolve(storageKey);
        Files.createDirectories(destination.getParent());
        Files.copy(content, destination);
        return storageKey;
    }

    @Override
    public InputStream read(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }

        Path relativePath = Path.of(storageKey);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Storage key must be relative");
        }

        Path resolvedPath = storageRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(storageRoot) || resolvedPath.equals(storageRoot)) {
            throw new IllegalArgumentException("Storage key must stay within the storage root");
        }
        return resolvedPath;
    }
}
