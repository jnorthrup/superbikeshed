package org.bereft.runtime

import java.security.KeyPair
import java.security.KeyPairGenerator

public
class KeyHandle(
    algName: String = "RSA",
    keylength: Int = 512,
    val keyGen: KeyPairGenerator = KeyPairGenerator.getInstance(algName).apply {
        initialize(keylength)
    },
) {

    public var pair: KeyPair = keyGen.generateKeyPair()

}

