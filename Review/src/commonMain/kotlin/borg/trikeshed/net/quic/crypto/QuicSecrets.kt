package borg.trikeshed.net.quic.crypto

data class QuicSecrets(
  val aeadKey: ByteArray,
  val aeadIv: ByteArray,
  val headerProtectionKey: ByteArray
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QuicSecrets) return false

    if (!aeadKey.contentEquals(other.aeadKey)) return false
    if (!aeadIv.contentEquals(other.aeadIv)) return false
    if (!headerProtectionKey.contentEquals(other.headerProtectionKey)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = aeadKey.contentHashCode()
    result = 31 * result + aeadIv.contentHashCode()
    result = 31 * result + headerProtectionKey.contentHashCode()
    return result
  }
}
