package moneyfan.io

/**
 * A mock implementation of [FileContentProvider] for testing purposes.
 * It allows simulating file existence and content without actual file I/O.
 */
class MockFileContentProvider : FileContentProvider {
    internal val mockFiles = mutableMapOf<String, List<String>>()

    /**
     * Adds a mock file with its content to the provider.
     * If a file with the same path already exists, its content will be overwritten.
     *
     * @param filePath The path of the mock file (e.g., "data/DOGEUSDT_1d_2021.csv").
     * @param lines The lines of content for the mock file.
     */
    fun addMockFile(filePath: String, lines: List<String>) {
        mockFiles[filePath] = lines
    }

    /**
     * Clears all mock files from the provider.
     */
    fun clearAllMockFiles() {
        mockFiles.clear()
    }

    override suspend fun readFileLines(filePath: String): List<String> {
        return mockFiles[filePath] ?: throw NoSuchElementException("Mock file not found: $filePath. Available files: ${mockFiles.keys}")
    }

    override suspend fun fileExists(filePath: String): Boolean {
        return mockFiles.containsKey(filePath)
    }
}
