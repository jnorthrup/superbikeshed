package borg.trikeshed.parse.bbcursive.vtables

import java.nio.ByteBuffer
import java.util.function.Function

/**
 * ref class -- approximation of c++ '&'
 * <p>
 * a documentation interface for a functional interface
 * <p>
 * this will reify a pojo
 * <p>
 * when the mutator function is complete, the {@link _ptr } is returned.
 * <p>
 * the implementation makes no guarantees about {@link java.nio.ByteBuffer#position } before or after the call.
 *
 * @param <endPojo> The java class to be sent to the bytes held by _ptr
 * @Author jim
 * @Date Sep 20, 2008 12:27:26 AM
 */

abstract class _mutator<endPojo> : Function<endPojo, _ptr> {
    private val context = ByteBufferContext()

    fun getContext(): ByteBufferContext {
        return context
    }

    /**this is a boilerplate cursor
     *
     */
    protected inner class ByteBufferContext : _edge<endPojo, _ptr>() {
        override fun at(): _ptr {
            return this.location()
        }

        override fun goTo(ptr: _ptr): _ptr {
            return at(ptr)
        }

        override fun r$(): _ptr {
            return r$()
        }

        fun apply(ptr: _ptr): endPojo {
            return apply(ptr)
        }
    }
    protected inner class StringifiedContext : _edge<String,ByteBufferContext>(){
        override fun at(): ByteBufferContext? {
            return null
        }

        override fun goTo(byteBufferContext: ByteBufferContext): ByteBufferContext? {
            return null
        }

        override fun r$(): ByteBufferContext? {
            return null
        }
    }


}