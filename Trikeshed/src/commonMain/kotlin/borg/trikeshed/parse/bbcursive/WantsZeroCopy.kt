package borg.trikeshed.parse.bbcursive

import borg.trikeshed.lib.ByteIndexedBuffer

/**
 * Created by jim on 8/8/14.
 */
interface WantsZeroCopy {
  fun asByteIndexedBuffer(): ByteIndexedBuffer
}