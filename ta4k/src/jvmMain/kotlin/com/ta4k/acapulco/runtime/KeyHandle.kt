package borg.trikeshed.acapulco.runtime

import java.security.KeyPair
import java.security.KeyPairGenerator

class KeyHandle(
    algName: String = "RSA",
    keylength: Int = 512,
    val keyGen: KeyPairGenerator = KeyPairGenerator.getInstance(algName).apply {
        initialize(keylength)
    },
) {

    var pair: KeyPair = keyGen.generateKeyPair()

}

