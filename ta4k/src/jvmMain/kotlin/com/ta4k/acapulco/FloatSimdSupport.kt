package borg.trikeshed.acapulco

import jdk.incubator.vector.DoubleVector
import jdk.incubator.vector.FloatVector
import vec.macros.Pai2

object FloatSimdSupport {

    @JvmStatic
    var threadLocalFMAChunkCache  = ThreadLocal.withInitial {
        val size = DoubleVector.SPECIES_PREFERRED.zero().length()
        val chunkCache: Array<DoubleArray> = Array(3) { DoubleArray(size) }

        let {
            val let = chunkCache.let { chunkCache ->
                val clen = chunkCache[0].size
                val booleanArray = BooleanArray(clen)
                (0 until clen).map { x ->
                    booleanArray.fill(true, 0, x)
                   DoubleVector.SPECIES_PREFERRED.loadMask(booleanArray, 0)
                }.toTypedArray()
            }
            val dArrays = Pai2( chunkCache   ,let)
            dArrays
        }
    }


}