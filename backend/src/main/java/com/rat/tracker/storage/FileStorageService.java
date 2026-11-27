package com.rat.tracker.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    String save(InputStream content) throws IOException;

    InputStream read(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    boolean exists(String storageKey);
}
