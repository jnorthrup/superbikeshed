package borg.trikeshed.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PlatformFile {
    private final File file;

    public PlatformFile(String path) {
        this.file = new File(path);
    }

    public boolean exists() {
        return file.exists();
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(Paths.get(file.getPath()));
    }
}
