def read_data_file(filepath: str) -> bytes:
    # Simulates reading raw bytes from a file.
    # In a real scenario, this would use:
    # with open(filepath, 'rb') as f:
    #   return f.read()
    return b"Sample file content for " + filepath.encode('utf-8')
