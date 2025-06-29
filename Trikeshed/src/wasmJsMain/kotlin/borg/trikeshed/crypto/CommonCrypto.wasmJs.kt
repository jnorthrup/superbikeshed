package borg.trikeshed.crypto

import borg.trikeshed.lib.*

/**
 * WasmJs implementation of CryptoFactory - simplified stub
 * 
 * Note: The full CommonCrypto interface requires proper implementations
 * with the new type system. This is a minimal stub to allow compilation.
 */
actual object CryptoFactory {
    actual fun createHasher(algorithm: HashAlgorithm): CommonCrypto.Hasher {
        TODO("WasmJs crypto implementation not yet available")
    }
    
    actual fun createSymmetricCipher(suite: CipherSuite): CommonCrypto.SymmetricCipher {
        TODO("WasmJs crypto implementation not yet available")
    }
    
    actual fun createKeyExchange(algorithm: KeyExchangeAlgorithm): CommonCrypto.KeyExchange {
        TODO("WasmJs crypto implementation not yet available")
    }
    
    actual fun createSigner(algorithm: SignatureAlgorithm): CommonCrypto.Signer {
        TODO("WasmJs crypto implementation not yet available")
    }
    
    actual fun createKeyDerivation(): CommonCrypto.KeyDerivation {
        TODO("WasmJs crypto implementation not yet available")
    }
    
    actual fun getSecureRandom(): CommonCrypto {
        TODO("WasmJs crypto implementation not yet available")
    }
}