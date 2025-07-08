package borg.trikeshed.crypto

import java.security.KeyPairGenerator
import java.security.KeyFactory
import javax.crypto.KeyAgreement
import javax.crypto.spec.DHParameterSpec
import java.security.spec.ECGenParameterSpec
import java.math.BigInteger

actual suspend fun performDHKeyExchange(): Pair<ByteArray, ByteArray> {
    // Simple DH implementation using Java crypto
    val dhParams = DHParameterSpec(
        BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF", 16),
        BigInteger("2"),
        1024
    )
    
    val keyGen = KeyPairGenerator.getInstance("DH")
    keyGen.initialize(dhParams)
    
    val aliceKeyPair = keyGen.generateKeyPair()
    val bobKeyPair = keyGen.generateKeyPair()
    
    val aliceKeyAgree = KeyAgreement.getInstance("DH")
    aliceKeyAgree.init(aliceKeyPair.private)
    aliceKeyAgree.doPhase(bobKeyPair.public, true)
    val aliceSharedSecret = aliceKeyAgree.generateSecret()
    
    val bobKeyAgree = KeyAgreement.getInstance("DH")
    bobKeyAgree.init(bobKeyPair.private)
    bobKeyAgree.doPhase(aliceKeyPair.public, true)
    val bobSharedSecret = bobKeyAgree.generateSecret()
    
    return aliceSharedSecret to bobSharedSecret
}

actual suspend fun performECDHKeyExchange(): Pair<ByteArray, ByteArray> {
    // Simple ECDH implementation using Java crypto
    val keyGen = KeyPairGenerator.getInstance("EC")
    keyGen.initialize(ECGenParameterSpec("secp256r1"))
    
    val aliceKeyPair = keyGen.generateKeyPair()
    val bobKeyPair = keyGen.generateKeyPair()
    
    val aliceKeyAgree = KeyAgreement.getInstance("ECDH")
    aliceKeyAgree.init(aliceKeyPair.private)
    aliceKeyAgree.doPhase(bobKeyPair.public, true)
    val aliceSharedSecret = aliceKeyAgree.generateSecret()
    
    val bobKeyAgree = KeyAgreement.getInstance("ECDH")
    bobKeyAgree.init(bobKeyPair.private)
    bobKeyAgree.doPhase(aliceKeyPair.public, true)
    val bobSharedSecret = bobKeyAgree.generateSecret()
    
    return aliceSharedSecret to bobSharedSecret
}