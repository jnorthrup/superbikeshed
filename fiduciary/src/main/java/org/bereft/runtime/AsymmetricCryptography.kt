package org.bereft.runtime

import org.apache.commons.codec.binary.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class AsymmetricCryptography(
    val algName: String = "RSA",
    val cipher: Cipher = Cipher.getInstance(algName),
    val keyFactory: KeyFactory = KeyFactory.getInstance(algName),
) {


    // https://docs.oracle.com/javase/8/docs/api/java/security/spec/PKCS8EncodedKeySpec.html
    fun getPrivate(filename: String): PrivateKey {
        val keyBytes = Files.readAllBytes(File(filename).toPath())
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return keyFactory.generatePrivate(spec)
    }

    // https://docs.oracle.com/javase/8/docs/api/java/security/spec/X509EncodedKeySpec.html
    fun getPublic(filename: String): PublicKey {
        val keyBytes = Files.readAllBytes(File(filename).toPath())
        val spec = X509EncodedKeySpec(keyBytes)
        return keyFactory.generatePublic(spec)
    }

    fun encryptFile(input: ByteArray, output: File, key: PrivateKey) {
        this.cipher.init(Cipher.ENCRYPT_MODE, key)
        writeToFile(output, this.cipher.doFinal(input))
    }

    fun decryptFile(input: ByteArray, output: File, key: PublicKey) {
        this.cipher.init(Cipher.DECRYPT_MODE, key)
        writeToFile(output, this.cipher.doFinal(input))
    }

    private fun writeToFile(output: File, toWrite: ByteArray) {
        val fos = FileOutputStream(output).use {
            it.write(toWrite)
        }
    }

    fun encryptText(msg: String, key: PrivateKey): String {
        this.cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeBase64String(cipher.doFinal(msg.toByteArray(charset("UTF-8"))))
    }

    fun decryptText(msg: String, key: PublicKey): String {
        this.cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(Base64.decodeBase64(msg)), Charsets.UTF_8)
    }

    fun getFileInBytes(f: File): ByteArray = FileInputStream(f).use {
        val fbytes = ByteArray(f.length().toInt())
        it.read(fbytes)

        return fbytes
    }

    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val ac = AsymmetricCryptography()
            val privateKey = ac.getPrivate("KeyPair/privateKey")
            val publicKey = ac.getPublic("KeyPair/publicKey")

            val msg = "Cryptography is fun!"
            val encrypted_msg = ac.encryptText(msg, privateKey)
            val decrypted_msg = ac.decryptText(encrypted_msg, publicKey)
            println("Original Message: " + msg +
                    "\nEncrypted Message: " + encrypted_msg
                    + "\nDecrypted Message: " + decrypted_msg)

            if (File("KeyPair/text.txt").exists()) {
                ac.encryptFile(ac.getFileInBytes(File("KeyPair/text.txt")),
                    File("KeyPair/text_encrypted.txt"), privateKey)
                ac.decryptFile(ac.getFileInBytes(File("KeyPair/text_encrypted.txt")),
                    File("KeyPair/text_decrypted.txt"), publicKey)
            } else {
                println("Create a file text.txt under folder KeyPair")
            }
        }
    }
}