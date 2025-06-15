package borg.trikeshed.acapulco.node.config

import org.bereft.runtime.AsymmetricCryptography
import org.bereft.runtime.info
import java.lang.Boolean.TRUE
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/**
 * checks the ENV for nodeVarName. gets that from env.
 * if empty, gets that java prop.
 * if either of those two work, logs to stderr
 *
 */
object NodeConfig {

    val insecure = System.getProperty("insecure", "false") == TRUE.toString()
    val sessionKey: String by lazy { reportConfig("mesh.id", System.getProperty("mesh.id", "testnode")) }

    fun qget2(nodeVarName: String, defaultVal: String?) = get2(
        nodeVarName, defaultVal, true)

    fun get2(nodeVarName: String, defaultVal: String?, quiet: Boolean = false): String? {
        assert("mesh.id" != nodeVarName) { "sessionKey is a special config.  dont use mesh.id in code only in the config" }
        val javapropname = "$sessionKey.${
            nodeVarName.lowercase(Locale.getDefault()).replace("^$sessionKey[_.]".toRegex(),
                "").replace(
                '_',
                '.')
        }"
        return (System.getenv(nodeVarName) ?: System.getProperty(javapropname) ?: defaultVal)?.also { cfg ->
            System.setProperty(javapropname, cfg)
            if (quiet.not() || insecure) reportConfig(javapropname, cfg)
        }
    }

    fun reportConfig(javapropname: String, `val`: String): String = `val`.apply {
        info("// -D$javapropname=\"$this\"")
    }


    /**
     * match  a pattern or lambda function
     */
    fun configMatches(
        configName: String, s1: String? = null,
        predicate: (String?) -> Boolean = { s -> s?.equals(s1) ?: false },
    ) = predicate(
        get2(configName, s1, false))

    /**
     * typically to configIs iv var=t or var=true
     */
    fun configIs(s: String, predicate: String = "true", s1: String? = null) = configMatches(
        s, s1,
        { predicate == s1 })

    /**
     * typically to configIs iv var=t or var=true
     */
    fun notConfig(s: String, predicate: String, s1: String? = null) = configIs(
        s, predicate, s1).not()

    fun getK(key: String, default: String) = get2(key, default, false)!!

    /**
     * defaults don't need null tests
     */
    fun get(s: String) = get2(s, null, false)

    fun qget(s: String) = qget2(s, null)

    /**
     * defaults don't need null tests
     */

//    val gsonBuilder = GsonBuilder().setDateFormat(getK("GSON_DATEFORMAT",
//        "yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
//        .setFieldNamingPolicy(FieldNamingPolicy.valueOf(
//            getK("GSON_FIELDNAMINGPOLICY", "IDENTITY"))).also {
//            if (configIs("GSON_PRETTY")) it.setPrettyPrinting()
//            if (configIs("GSON_NULLS")) it.serializeNulls()
//            if (configIs("GSON_NANS")) it.serializeSpecialDoubleingPointValues()
//        }
//
//    val gson = gsonBuilder.setPrettyPrinting().create()!!


}

val keypairPrivate: String? by lazy { NodeConfig.qget("keypair.private") }
val keypairPublic: String? by lazy { NodeConfig.qget("keypair.public") }
val asymmetricCryptography: AsymmetricCryptography by lazy {
    AsymmetricCryptography(NodeConfig.qget2("keypair.cipher",
        "RSA")!!)
}
val keypair_private by lazy {
    asymmetricCryptography.keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keypairPrivate)))
}
val keypair_public by lazy {
    asymmetricCryptography.keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(keypairPublic)))
}


fun String.configDecrypt(): String = if (keypairPublic == null) this else keypairPublic.let { decr() }

fun String.configCrypt(): String = if (keypairPrivate == null) this else keypairPrivate.let { encr() }

fun String.decr(): String = try {
    asymmetricCryptography.decryptText(inflate(), keypair_public)
} catch (e: Exception) {
    this
}

fun String.encr(): String = try {
    asymmetricCryptography.encryptText(deflate(), keypair_private)
} catch (e: Exception) {
    this
}


fun String.inflate(): String = //    val inflater = Inflater()
//    inflater.setInput(toByteArray(UTF_8))
//    val byteArray = ByteArray(max(4096, length * 12))
//    val inflateCount = inflater.inflate(byteArray)
//
//    val x = (inflater.finished())
//    val sliceArray = byteArray.sliceArray(0..inflateCount)
//    val msg = String(sliceArray)
    this

fun String.deflate(): String = //    val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
//    deflater.setInput(toByteArray())
//    val byteArray = ByteArray(max(256, length * 2))
//    deflater.finish()
//    val deflateCount = deflater.deflate(byteArray)
//    val bytesWritten = deflater.bytesWritten
//    val bytes = byteArray.sliceArray(0..deflateCount)
//    val msg = String(bytes)
//    return msg
    this

fun main(args: Array<String>) {
    val s = "abc_defghij-klmnopqzrstuvwxyz"
    val deflate = s.deflate()
    println(deflate)
    val inflate = deflate.inflate()
    println(inflate)
}