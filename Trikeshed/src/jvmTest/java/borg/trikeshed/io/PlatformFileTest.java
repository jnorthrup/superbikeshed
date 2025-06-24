package borg.trikeshed.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlatformFileTest {

    @TempDir
    Path tempDir;

    private PlatformFile platformFile;
    private File testFile;
    private File testDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary file
        testFile = Files.createFile(tempDir.resolve("testFile.txt")).toFile();
        Files.write(testFile.toPath(), "Hello World".getBytes());

        // Create a temporary directory
        testDir = Files.createDirectory(tempDir.resolve("testDir")).toFile();
    }

    @Test
    void testFileExists() {
        platformFile = new PlatformFile(testFile.getAbsolutePath());
        assertTrue(platformFile.exists(), "File should exist");

        PlatformFile nonExistentFile = new PlatformFile(tempDir.resolve("nonExistent.txt").toString());
        assertFalse(nonExistentFile.exists(), "File should not exist");
    }

    @Test
    void testIsDirectory() {
        platformFile = new PlatformFile(testDir.getAbsolutePath());
        assertTrue(platformFile.isDirectory(), "Should be a directory");

        PlatformFile fileAsDir = new PlatformFile(testFile.getAbsolutePath());
        assertFalse(fileAsDir.isDirectory(), "Should not be a directory");
    }

    @Test
    void testReadAllBytes() throws IOException {
        platformFile = new PlatformFile(testFile.getAbsolutePath());
        byte[] expectedBytes = "Hello World".getBytes();
        byte[] actualBytes = platformFile.readAllBytes();
        assertArrayEquals(expectedBytes, actualBytes, "File content should match");
    }

    @Test
    void testReadAllBytesNonExistentFile() {
        platformFile = new PlatformFile(tempDir.resolve("nonExistent.txt").toString());
        assertThrows(IOException.class, () -> platformFile.readAllBytes(),
                "Should throw IOException for non-existent file");
    }
}
