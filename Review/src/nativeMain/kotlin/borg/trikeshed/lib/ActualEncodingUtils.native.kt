package borg.trikeshed.lib

import kotlinx.cinterop.*
import platform.openssl.*

actual fun base64Encode(input: String): String {
    return base64Encode(input.encodeToByteArray()) // Convert String to UTF-8 ByteArray first
}

actual fun base64Encode(input: ByteArray): String = memScoped {
    if (input.isEmpty()) return ""

    val inputPinned = input.pin()
    // BIO_s_mem() creates a memory BIO
    val bioOut = BIO_new(BIO_s_mem()) ?: throw RuntimeException("Failed to create memory BIO for Base64 encoding")
    // BIO_f_base64() creates a Base64 filter BIO
    val bioB64 = BIO_new(BIO_f_base64()) ?: run { BIO_free_all(bioOut); throw RuntimeException("Failed to create Base64 filter BIO") }
    BIO_set_flags(bioB64, BIO_FLAGS_BASE64_NO_NL) // No newlines in output

    // Chain the BIOs: bioB64 -> bioOut. Output of bioB64 goes to bioOut.
    val bioChain = BIO_push(bioB64, bioOut)
    try {
        val bytesWritten = BIO_write(bioChain, inputPinned.addressOf(0), input.size)
        // For BIO_write with a filter like Base64, it might not write all bytes in one go or might return a value
        // indicating success even if not all data is processed yet (needs BIO_flush).
        // Or it might return <=0 on error.
        if (bytesWritten <= 0) {
             if (BIO_should_retry(bioChain) == 0) { // Check if it's a non-retryable error
                throw RuntimeException("BIO_write failed for Base64 encoding: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
             }
             // This path for retryable errors might not be hit often with memory BIOs for simple writes.
             // If it were a socket BIO, retry logic would be more relevant.
        }
        // Ensure all data is flushed through the filter chain
        BIO_flush(bioChain)

        // Retrieve the memory pointer from the memory BIO (bioOut)
        val memBioPtr = alloc<CPointerVar<BUF_MEM>>()
        BIO_get_mem_ptr(bioOut, memBioPtr.ptr) // Fills memBioPtr with pointer to BUF_MEM structure
        val bufMem = memBioPtr.value ?: throw RuntimeException("Failed to get memory pointer from BIO")

        // Read the data from the BUF_MEM structure
        // bufMem.pointed.data is a CPointer<ByteVar> (char*)
        // bufMem.pointed.length is the length of the data in the buffer
        return bufMem.pointed.data?.readBytes(bufMem.pointed.length.toInt())?.decodeToString()
               ?: throw RuntimeException("Failed to read encoded data from BIO buffer or data is null")

    } finally {
        BIO_free_all(bioChain) // This frees bioOut and bioB64
        inputPinned.unpin()
    }
}
