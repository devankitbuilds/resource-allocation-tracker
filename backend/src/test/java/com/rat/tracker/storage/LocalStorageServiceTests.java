package com.rat.tracker.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesReadsAndDeletesFileBytes() throws IOException {
        FileStorageService storage = new LocalStorageService(temporaryDirectory.toString());
        byte[] expected = "synthetic resume content".getBytes(StandardCharsets.UTF_8);

        String storageKey = storage.save(new ByteArrayInputStream(expected));

        assertTrue(storageKey.matches("\\d{4}/\\d{2}/[0-9a-fA-F-]{36}"));
        assertTrue(storage.exists(storageKey));
        try (InputStream savedContent = storage.read(storageKey)) {
            assertArrayEquals(expected, savedContent.readAllBytes());
        }

        storage.delete(storageKey);

        assertFalse(storage.exists(storageKey));
    }

    @Test
    void rejectsStorageKeysThatEscapeTheStorageRoot() {
        FileStorageService storage = new LocalStorageService(temporaryDirectory.toString());

        assertThrows(IllegalArgumentException.class, () -> storage.exists("../../outside"));
    }
}
