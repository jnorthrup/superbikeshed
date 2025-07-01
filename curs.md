===============================
=== bbcursive/src/test/java ===
===============================

===========================================================
=== bbcursive/src/test/java/bbcursive/lib/opt_Test.java ===
===========================================================
package bbcursive.lib;

import org.junit.Test;

import java.nio.ByteBuffer;

import static bbcursive.lib.allOf_.allOf;
import static bbcursive.lib.chlit_.chlit;
import static bbcursive.lib.opt_.opt;
import static bbcursive.std.bb;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertNull;

/**
 * Created by jim on 1/24/16.
 */
public class opt_Test {
    @Test
    public void testChlit(){
        ByteBuffer aa = bb("aba", chlit('a'), opt(chlit('a')), opt(chlit('c')), opt(chlit('b')), chlit('a'));
        assertNotNull(aa);
        aa = bb("aba", allOf(chlit('a'), opt(chlit('a')), opt(chlit('c')), opt(chlit('b')), chlit('z')));
        assertNull(aa);
        aa = bb("aba", allOf(chlit('a'), opt(chlit('a')), opt(chlit('b')), opt(chlit('z')), chlit('a'), opt(chlit('a'))));
        assertNotNull(aa);
    }
}
==============================================================
=== bbcursive/src/test/java/bbcursive/lib/repeat_Test.java ===
==============================================================
package bbcursive.lib;

import bbcursive.std;
import org.junit.Test;

import java.nio.ByteBuffer;

import static bbcursive.lib.chlit_.chlit;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by jim on 1/23/16.
 */
public class repeat_Test {


    @Test
    public void testRepeat(){



        ByteBuffer aaa = std.bb("aaa", repeat_.repeat(chlit('a')));
        assertNotNull(aaa);
        aaa = std.bb("aba", repeat_.repeat(chlit('a')));
        assertNotNull(aaa);
        aaa = std.bb("baa", repeat_.repeat(chlit('a')));
        assertNull(aaa);
        std.flags.get().add(std.traits.skipper);
        aaa = std.bb("a a a", repeat_.repeat(chlit('a')));
        assertNotNull(aaa);
        std.flags.get().remove(std.traits.skipper);
        aaa = std.bb(" a a a", repeat_.repeat(chlit('a')));
        assertNull(aaa);

    }

}
==========================================================
=== bbcursive/src/test/java/bbcursive/lib/Narsive.java ===
==========================================================
package hacks;

import bbcursive.Cursive;
import bbcursive.std;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static bbcursive.Cursive.pre.*;
import static hacks.Utils.*;
import static hacks.advanceTo.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.EnumSet.of;

public enum Narsive implements Cursive {
    task {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, opt(budget), sentence);
        }
    },
    sentence() {


        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(judgement, goal, question, desire));
        }
    },
    judgement {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, statement, dot, opt(tense), opt(truth));
        }
    },
    goal {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, exclamation, opt(truth));
        }
    },
    desire{
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, amp, opt(tense));
        }
    },
    question {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, questionMark, opt(tense));
        }
    },

    listEnd {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return null;//todo: never
        }
    },
    relationship {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, lt, term, copula, term, gt);
        }
    },
    statement {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return terminatingOr(buffer, of(relationship, operation, term));
        }
    }, tense {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(Tense.values()));
        }

    },
    truth {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, percent, frequency, percent, frequency, opt(semicolon, confidence), percent);
        }
    },
    budget {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, dollarSign, priority, opt(semicolon, durability), dollarSign);
        }
    },
    copula {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(Copula.values()));
        }
    }, term {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {

            return $(buffer, anyOf(word, variable, compoundTerm, statement));
        }
    },

    operation {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, new ListParser("(^", word, term, ")"));
        }
    },

    variable {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(NarVar.values()));
        }
    }, compoundTerm {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, new ListParser("(", conjunction, term, ")"));

        }
    }, conjunction {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(Conjunction.values()));
        }
    },
    frequency{
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, numeric);
        }
    }, confidence {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, numeric);
        }
    }, priority {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, numeric);
        }
    }, durability {
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            return $(byteBuffer, numeric);
        }
    },
    quotedString {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            $(buffer, quot, b -> {
                consumeString(b);
                return b;
            });
            return buffer;
        }
    },
    numeric{
        @Override
        public ByteBuffer apply(ByteBuffer byteBuffer) {
            byte b = ((ByteBuffer) byteBuffer.mark()).get();

            boolean sign = b == '-' || b == '+';
            if (!sign) byteBuffer.reset();

            boolean dot1 = false;
            boolean etoken = false;
            boolean esign = false;
            while (byteBuffer.hasRemaining()) {
                int c=0;
                while (byteBuffer.hasRemaining() && Character.isDigit(b = ((ByteBuffer) byteBuffer.mark()).get())) c++;

                switch (b) {
                    case '.':
                        assert !dot1 : "extra dot";
                        dot1 = true;
                    case 'E':
                    case 'e':
                        assert !etoken : "missing digits or redundant exponent";
                        etoken = true;
                    case '+':
                    case '-':
                        assert !esign : "bad exponent sign";
                        esign = true;
                    default:
                        if (!Character.isDigit(b))
                            return c > 0 ? $(byteBuffer, byteBuffer.hasRemaining() ? back1 : noop): null;
                }
            }
            return null;
        }
    },
    word {
        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            return $(buffer, anyOf(buf -> {
                int c = 0;
                while (buf.hasRemaining() && isValidAtomChar(buf.get())) c++;
                return c > 0 ? $(buf, back1) : null;
            }, quotedString));
        }
    },
    ;

    private static Cursive anyOf(Cursive... anyOf) {

        return b -> {
            for (Cursive o : anyOf) {
                ByteBuffer bb = $(b, o);
                if (null != bb) {
                    return bb;
                }
            }

            return null;
        };
    }

    private static Cursive zeroOrMore(Cursive sep, Narsive listNode, ArrayList<Integer> integers) {

        return buf -> {
            int rollback = buf.position();
            int c = 0;

            while (null != $(buf, sep)) {
                int position = buf.position();
                if (null == $(buf, listNode)) return $(buf, pos(rollback), null);

                integers.add(position);
                c++;
            }

            return c > 0 ? buf : $(buf, pos(rollback), null);
        };

    }

    public static Cursive confix(String begin, Cursive clause, String end, AtomicInteger contentIndex) {
        return buf -> {
            int position = $(buf, skipWs, mark).position();
            ByteBuffer gotBegin = genericAdvance(buf, begin.getBytes());
            if (null != gotBegin) {
                contentIndex.set(buf.position());
                if (null != $(buf, clause, skipWs)) {
                    ByteBuffer byteBuffer = genericAdvance(buf, end.getBytes());
                    if (null != byteBuffer) return buf;
                }
            }
            return $(buf, pos(position), null);
        };

    }


    public static final int MASK24BITS = 0xffffff;


    void recordFeature(int position) {
        features.put(ordinal() << 24 | position & 0xffffff);
    }


    static IntBuffer features = IntBuffer.allocate(1000); //8/24 bit flags/offsets


    ByteBuffer terminatingOr(ByteBuffer buffer, Iterable<? extends Cursive> judgement) {
        buffer = $(buffer, skipWs, mark);
        int position = buffer.position();
        for (Cursive cursive : judgement) {
            ByteBuffer bb = $(buffer, reset, cursive);
            if (null != bb) {
                recordFeature(position);
                listEnd.recordFeature(bb.position());
                return bb;
            }
        }
        return null;
    }

    private static class Constants {
        private static ListParser compundListParser;
    }


    private class ListParser implements Cursive {

        private final String begin;
        private final Narsive firstFeature;
        private final Narsive listNode;
        private final String end;

        public ListParser(String begin, Narsive firstFeature, Narsive listNode, String end) {
            this.begin = begin;
            this.firstFeature = firstFeature;
            this.listNode = listNode;
            this.end = end;
        }


        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            AtomicInteger middlePosition = new AtomicInteger();
            ArrayList<Integer> integers = new ArrayList<>();
            int rollback = buffer.position();
            if (null != $(buffer, confix(begin, buf -> $(buf, firstFeature, zeroOrMore(comma, listNode, integers)), end, middlePosition))) {
                recordFeature(rollback);
                firstFeature.recordFeature(middlePosition.get());
                for (Integer integer : integers) listNode.recordFeature(integer);
                listEnd.recordFeature(buffer.position());
                return buffer;
            }
            return std.bb(buffer, pos(rollback), null);
        }
    }

    public static void main(String[] args) {
        String input = "(a --> b)";
        ByteBuffer encode = UTF_8.encode(input);


    }

    public static Cursive opt(Cursive... allOrPrevious) {
        return byteBuffer -> {

            ByteBuffer bb = $(byteBuffer, allOrPrevious);
            return null == bb
                    ? byteBuffer : bb;
        };
    }

    public static ByteBuffer $(ByteBuffer b, Cursive... allOrNull) {
        int position = b.position();

        ByteBuffer bb = std.bb(
                b, allOrNull
        );
        return bb != null ? bb : std.bb(b, pos(position), null);
    }
}

===============================
=== bbcursive/src/main/java ===
===============================

========================================================
=== bbcursive/src/main/java/bbcursive/Allocator.java ===
========================================================
package bbcursive;

import java.nio.ByteBuffer;

import static bbcursive.lib.log.log;

/**
 * User: jim
 * Date: Oct 6, 2007
 * Time: 3:10:32 AM
 */
public class Allocator {

     ByteBuffer DIRECT_HEAP;
    public static int MEG = (1<<10)<<10,BLOCKSIZE=MEG*2;

    private  int initialCapacity =Runtime.getRuntime().availableProcessors()*20*2;


    public  final ByteBuffer EMPTY_SET = ByteBuffer.allocate(0).asReadOnlyBuffer() ;

     int size = initialCapacity;

    public Allocator(int... bytes) {
        if(bytes.length>0)
            initialCapacity = bytes[0];

        ByteBuffer buffer = null;
        while (buffer == null)
            try {

                if (isDirect())
                    buffer = (ByteBuffer) ByteBuffer.allocateDirect(size) .limit(0);
                else
                    buffer = (ByteBuffer) ByteBuffer.allocate(size) .limit(0);

                DIRECT_HEAP = buffer;
                log("Heap allocated at " + size / MEG + " megs");
                size *= 2;

            } catch (IllegalArgumentException e) {
                size = Math.max(16 * MEG, size / 2);
                System.gc();
            } catch (OutOfMemoryError e) {
                size = Math.max(16 * MEG, size / 2);
                System.gc();
            }
    }

    private  void init() {

        ByteBuffer buffer = null;
        while (buffer == null)
            try {

                if (isDirect())
                    buffer = (ByteBuffer) ByteBuffer.allocateDirect(size) .limit(0);
                else
                    buffer = (ByteBuffer) ByteBuffer.allocate(size) .limit(0);

                DIRECT_HEAP = buffer;
                log("Heap allocated at " + size / MEG + " megs");
                size *= 2;

            } catch (IllegalArgumentException e) {
                size = Math.max(16 * MEG, size / 2);
                System.gc();
            } catch (OutOfMemoryError e) {
                size = Math.max(16 * MEG, size / 2);
                System.gc();
            }
    }

    ByteBuffer allocate(int size) {
        if (size == 0) return EMPTY_SET;
        try {
            DIRECT_HEAP.limit(DIRECT_HEAP.limit() + size);
        } catch (IllegalArgumentException e) {
            init();
            return allocate(size);
        }
        ByteBuffer ret = (ByteBuffer) DIRECT_HEAP.slice().limit(size).mark();
        DIRECT_HEAP.position(DIRECT_HEAP.limit());
        return ret;
    }

    public  boolean isDirect() {
        return false;
    }

}

==========================================================
=== bbcursive/src/main/java/bbcursive/ann/Skipper.java ===
==========================================================
package bbcursive.ann;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by jim on 1/18/16.
 */@Retention(RetentionPolicy.RUNTIME)
public @interface Skipper
{
}

==============================================================
=== bbcursive/src/main/java/bbcursive/ann/ForwardOnly.java ===
==============================================================
package bbcursive.ann;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * negates Backtracking
 */@Retention(RetentionPolicy.RUNTIME)
public @interface ForwardOnly
{
}

===============================================================
=== bbcursive/src/main/java/bbcursive/ann/Backtracking.java ===
===============================================================
package bbcursive.ann;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by jim on 1/18/16.
 */@Retention(RetentionPolicy.RUNTIME)
public @interface Backtracking
{
}

=============================================================
=== bbcursive/src/main/java/bbcursive/ann/ParseDebug.java ===
=============================================================
package bbcursive.ann;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
  */@Retention(RetentionPolicy.RUNTIME)
public @interface ParseDebug
{
}

========================================================
=== bbcursive/src/main/java/bbcursive/ann/Infix.java ===
========================================================
package bbcursive.ann;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *negates skipper
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Infix
{

}

============================================================
=== bbcursive/src/main/java/bbcursive/vtables/_edge.java ===
============================================================
package bbcursive.vtables;

import java.util.Objects;

/**
 * context class.   midpoint between 2 casts.  this class is a pair, but we pretend its more. this should be refactored
 * to a pair class.
 * <p>
 * Function interface performs reification from the addressType against the core
 * type,context, delta, coersion points, etc.
 * <p>
 * <p>
 * left refers to "refreence" side, right refers to "pointer" side.
 * <p>
 * User: jim
 * Date: Sep 18, 2008
 * Time: 6:05:14 AM
 */
public abstract class _edge<coreType, addressType> {
    /**
     * this is a core of memory accessed somehow by an addressType to get java Objects from this context of core that is probably bytes or chars
     */
    private coreType core;

    protected abstract addressType at();

    protected abstract addressType goTo(addressType addressType);



    /**
     * left type node with induction of core only.  address will be null until set
     *
     * @param e
     * @return
     */
    public coreType core(_edge<coreType, addressType>... e) {
        boolean empty = 0 == e.length;

        boolean isMe = !empty&&this == e[0];

        return !empty && !isMe ? core(bind(e[0].core(), e[0].location())) : core;

    }

    /**
     * an address
     * <p>
     * for _ptr, Integer is an address of a ByteBuffer state, linear memory here.
     * <p>
     * for {@code Map<K,V>}, K is an address to get a V from {@code _edge<V,K>}
     * <p>
     * for {@code _edge<_edge<A,B>,_ptr>}
     *
     * @param notnullorself null for self.  non-empty set for induction
     * @return typically what is returned is what is passed in most recently to any of the Pair.second mutators (this.at, this.goto, this.location).
     */
    protected final addressType at(addressType... notnullorself) {
        addressType addressType1 = notnullorself[0];
        return 0 != notnullorself.length && !Objects.equals(this, addressType1) ? goTo(addressType1) : r$();
    }

    /**
     * internal factory or getter for pair.second.  for _ptr this is inferred from bytebuffer instance.
     *
     * @return
     */
    protected abstract addressType r$();

    /**
     * right type node with induction
     *
     * @param e
     * @return
     */
    public final addressType location(_edge<coreType, addressType>... e) {
        _edge<coreType, addressType> subj = this;
        boolean empty = 0 == e.length;
        boolean alien = !empty &&subj != e[0];
        return empty||!alien ? at() :bind(e[0].core(), at(e[0].location())).location();
    }

    /**
     * binds two types
     *
     * @param coreType
     * @param address
     * @return fused arc
     */
    public _edge<coreType, addressType> bind(coreType coreType
            , addressType address) {
        core= (coreType);
        at(address);
        return this;
    }


}
/**
 * public interface €<Ω, µ> extends _proto<Ω> { Ω Ω(€<Ω, µ> €); µ µ(€<Ω, µ> €); €<Ω, µ> €(Ω Ω, µ µ);}
 */
==============================================================
=== bbcursive/src/main/java/bbcursive/vtables/CString.java ===
==============================================================
package bbcursive.vtables;

import bbcursive.std;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static bbcursive.Cursive.pre.*;
import static bbcursive.std.bb;
import static bbcursive.std.str;

/**
 * this class reads a null terminated string. the null is not included
 */

public class CString {
    private final _mutator$ mutator = new _mutator$();
    private final _reifier$ reifier = new _reifier$();
    public _mutator<String> getMutator() {
        return mutator;
    }
    public _reifier<String> getReifier() { return reifier; }

    private class _mutator$ extends _mutator<String> {
        @Override
        public _ptr apply(String s) {

            ByteBufferContext context = getContext();
            _ptr at = context.at();
            bb(std.cat(bb(at.core(), mark), StandardCharsets.UTF_8.encode(s)), duplicate, reset, slice, debug);//wordy, but not doing much
            return getContext().at();
        }
    }
    private class _reifier$ implements _reifier<String> {
        @Override
        public String apply(_ptr ptr) {
            ByteBuffer b = bb(ptr.core(ptr), mark, slice);
            while (b.hasRemaining() && b.get() > 0) ;
            return str(b.flip());
        }
    }
}

===============================================================
=== bbcursive/src/main/java/bbcursive/vtables/_reifier.java ===
===============================================================
package bbcursive.vtables;

import java.util.function.Function;

/** a documentation interface for a functional interface
 *
 * this will reify a pojo
 *
 * when the reification is complete, the value is returned.  the implementation makes no guarantees about ByteBuffer position before or after the call.
 *
 *
 * Created by jim on 1/5/2016.
 */
public interface _reifier<endPojo> extends Function<_ptr, endPojo> {

    /**
     *
     * @param ptr bytebuffer and position sensor virtual pair
     * @return
     */
    @Override
    endPojo apply(_ptr ptr);
}

===========================================================
=== bbcursive/src/main/java/bbcursive/vtables/_ptr.java ===
===========================================================
package bbcursive.vtables;

import java.nio.ByteBuffer;

/**
 * pointer class -- approximation of c++ '*'
 * <p>
 * this class is not exactly a Pair, it is a ByteBuffer reference with a settable position() sensor designed only for DirectByteBuffer work.
 *
 * @author jim
 */
public class _ptr extends _edge<ByteBuffer, Integer> {
    @Override
    protected Integer at() {
        return r$();
    }

    /**
     * bb pos
     *
     * @param integer
     * @return
     */
    @Override
    protected Integer goTo(Integer integer) {
        core().position(integer);
        return integer;
    }

    @Override
    protected Integer r$() {
        return core().position();
    }
}
===============================================================
=== bbcursive/src/main/java/bbcursive/vtables/_mutator.java ===
===============================================================
package bbcursive.vtables;

import java.util.function.Function;

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

public abstract class _mutator<endPojo> implements Function<endPojo, _ptr> {
    private final ByteBufferContext context = new ByteBufferContext();

    public ByteBufferContext getContext() {
        return context;
    }

    /**this is a boilerplate cursor
     *
     */
    protected class ByteBufferContext extends _edge<endPojo, _ptr> {
        protected _ptr at() {
            return this.location();
        }

        protected _ptr goTo(_ptr ptr) {
            return at(ptr);
        }

        protected _ptr r$() {
            return r$();
        }

        public endPojo apply(_ptr ptr) {
            return apply(ptr);
        }
    }
    protected class StringifiedContext extends _edge<String,ByteBufferContext>{
        @Override
        protected ByteBufferContext at() {
            return null;
        }

        @Override
        protected ByteBufferContext goTo(ByteBufferContext byteBufferContext) {
            return null;
        }

        @Override
        protected ByteBufferContext r$() {
            return null;
        }
    }


}

===================================================================
=== bbcursive/src/main/java/bbcursive/vtables/package-info.java ===
===================================================================
/**
 * Created by jim on 1/31/16.
 */
package bbcursive.vtables;
==================================================
=== bbcursive/src/main/java/bbcursive/std.java ===
==================================================
package bbcursive;

import bbcursive.ann.Backtracking;
import bbcursive.ann.ForwardOnly;
import bbcursive.ann.Infix;
import bbcursive.ann.Skipper;
import bbcursive.lib.u8tf;
import bbcursive.vtables._edge;
import bbcursive.vtables._ptr;
import com.databricks.fastbuffer.ByteBufferReader;
import com.databricks.fastbuffer.JavaByteBufferReader;
import com.databricks.fastbuffer.UnsafeDirectByteBufferReader;
import com.databricks.fastbuffer.UnsafeHeapByteBufferReader;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.lang.Character.isDigit;
import static java.lang.Character.isWhitespace;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.copyOfRange;
import static java.util.Arrays.deepToString;
import static java.util.EnumSet.copyOf;
import static java.util.EnumSet.noneOf;


/**
 * Created by jim on 8/8/14.
 */
public class std {


    final private static boolean debug_bbcursive = true;// Objects.equals("true", System.getenv("debug_bbcursive"));
    private static Allocator allocator;

    /**
     * the outbox -- when a parse term successfully returns and a {@link Consumer}is installed as the outbox the
     * following state is published allowing for a recreation of the event elsewhere within the jvm
     * <p>
     * in reverse order of resolution:
     * <p>
     * flags -- from annotations from lambda class
     * UnaryOperator -- the lambda that fired,
     * Integer -- length, to save time moving and scoring the artifact
     * _ptr -- _edge[ByteBuffer,Integer] state pair
     */
    public static ThreadLocal<Consumer<_edge<_edge<Set<traits>,
            _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>>>
            outbox = ThreadLocal.withInitial(() -> new Consumer<_edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>>() {
        @Override
        public void accept(_edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr> edge_ptr_edge) {
            // exhaust core()+location() fanout in intellij for a representational constant
            // automate later.
            _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr> edge_ptr_edge1 = edge_ptr_edge;
            _ptr location = edge_ptr_edge1.location();
            Integer startPosition = location.location();
            _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>> set_edge_edge = edge_ptr_edge1.core();
            Set<traits> traitsSet = set_edge_edge.core();
            _edge<UnaryOperator<ByteBuffer>, Integer> operatorIntegerEdge = set_edge_edge.location();
            Integer endPosition = operatorIntegerEdge.location();
            UnaryOperator<ByteBuffer> unaryOperator = operatorIntegerEdge.core();
            String s = deepToString(new Integer[]{startPosition, endPosition});
            System.err.println("+++ " + s + unaryOperator + " " + traitsSet);

        }
    });

    /**
     * when you want to change the behaviors of the main IO parser, insert a new {@link BiFunction} to intercept
     * parameters and returns to fire events and clean up using {@link ThreadLocal#set(Object)}
     */
    public enum traits {
        debug, backtracking, skipper;

    }


    public static final ThreadLocal<Set<traits>> flags = ThreadLocal.withInitial((Supplier<? extends Set<traits>>) () -> noneOf(traits.class));


    /**
     * this is the main bytebuffer io parser most easily coded for.
     *
     * @param b   the bytebuffer
     * @param ops
     * @return
     */
    public static ByteBuffer bb(ByteBuffer b, UnaryOperator<ByteBuffer>... ops) {
        ByteBuffer r = null;
        Set<traits> restoration = null;
        UnaryOperator<ByteBuffer> op = null;
        if (null != b && 0 < ops.length && null != (op = ops[0])) {
            ;
            if (debug_bbcursive) System.err.println("??? " + op);
            int startPosition = b.position();

            if (flags.get().contains(traits.skipper)) {
                boolean rem=false;
                while ((rem = b.hasRemaining()) && isWhitespace(((ByteBuffer) b.mark()).get() & 0xff));
                if (rem) b.reset();
            }
            restoration = induct(op.getClass());
            switch (ops.length) {
                case 0:
                    r = b;
                    break;
                case 1:
                    r = op.apply(b);
                    break;

/*save
                case 2:
                    r = bb(bb(b, op), ops[1]);
                    break;
                case 3:
                    r = bb(bb(bb(b, op), ops[1]), ops[2]);
                    break;
                case 4:
                    r = bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]);
                    break;
                case 5:
                    r = bb(bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]), ops[4]);
                    break;
                case 6:
                    r = bb(bb(bb(bb(bb(bb(b, op), ops[1]), ops[2]), ops[3]), ops[4]), ops[5]);
                    break;
*/

                default:
                    r = /*bb(bb(bb(bb(*/bb(bb(b, op), copyOfRange(ops, 1, ops.length));
                    break;
            }

            if (null == r && flags.get().contains(traits.backtracking)) {
                if (debug_bbcursive)
                    System.err.println("--- " + deepToString(new Integer[]{startPosition, b.position()}) + " " + String.valueOf(op));
                r = (ByteBuffer) b.position(startPosition);

            } else if (null != outbox.get()) {
                onSuccess(b, op, startPosition);
            }

        }
        if (restoration != null)
            flags.set(restoration);
        return r;
    }

    public
    static void onSuccess(ByteBuffer b, UnaryOperator<ByteBuffer> byteBufferUnaryOperator, int startPosition) {
        int endPos = b.position();
        Set<traits> immutableTraits = copyOf(flags.get());

        /**
         * creates a slice.  probably a bad idea due to array() b000gz
         */
        std.outbox.get().accept(createSuccessTuple(b, byteBufferUnaryOperator, startPosition, endPos, immutableTraits));
    }

    @NotNull
    public static _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr> createSuccessTuple(final ByteBuffer b, final UnaryOperator<ByteBuffer> byteBufferUnaryOperator, final int startPosition, final int endPos, final Set<traits> immutableTraits) {
        return new _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>() {
            @Override
            protected _ptr at() {
                return r$();
            }

            @Override
            protected _ptr goTo(_ptr ptr) {
                throw new Error("trifling with an immutable pointer");
            }

            /**
             * this binds a pointer to a pair of ByteBuffer and Integer.  note the bytebuffer is mutated by this
             * operation and will corrupt the source stream if this isn't a slice or a duplicate
             *
             *
             * @return the _ptr
             */
            @Override

            protected _ptr r$() {

                return (_ptr) new _ptr().bind(
                        (ByteBuffer) b.duplicate().limit(endPos), startPosition);
            }

            @Override
            public _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>> core(_edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>... e) {
                return new _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>() {
                    @Override
                    public Set<traits> core(_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>... e) {
                        return immutableTraits;
                    }

                    @Override
                    protected _edge<UnaryOperator<ByteBuffer>, Integer> at() {
                        return r$();
                    }

                    @Override
                    protected _edge<UnaryOperator<ByteBuffer>, Integer> goTo(_edge<UnaryOperator<ByteBuffer>, Integer> unaryOperatorInteger_edge) {
                        throw new Error("cant move this");
                    }

                    @Override
                    protected _edge<UnaryOperator<ByteBuffer>, Integer> r$() {
                        return new _edge<UnaryOperator<ByteBuffer>, Integer>() {
                            @Override
                            protected Integer at() {
                                return r$();
                            }

                            @Override
                            protected Integer goTo(Integer integer) {
                                throw new Error("immutable");
                            }

                            @Override
                            public UnaryOperator<ByteBuffer> core(_edge<UnaryOperator<ByteBuffer>, Integer>... e) {
                                return byteBufferUnaryOperator;
                            }

                            @Override
                            protected Integer r$() {
                                return endPos;
                            }
                        };
                    }
                };
            }
        };
    }


    static Map<Class, Set<traits>> termCache = new WeakHashMap<>();

    /**
     * cache terminal flags and use them by class.
     * <p>
     * if class is gc'd, no leak.
     *
     * @param aClass
     * @return the previous (restoration) state
     */
    static Set<traits> induct(Class<? extends UnaryOperator> aClass) {
        Set<traits> c = flags.get();
        Set<traits> traitses = copyOf(c);
        AtomicBoolean dirty = new AtomicBoolean(false);
        if (aClass.isAnnotationPresent(Skipper.class)) {
            dirty.set(true);
            c.add(traits.skipper);
        } else if (aClass.isAnnotationPresent(Infix.class)) {
            dirty.set(true);
            c.remove(traits.skipper);
        }
        if (aClass.isAnnotationPresent(Backtracking.class)) {
            dirty.set(true);
            c.add(traits.backtracking);
        } else if (aClass.isAnnotationPresent(ForwardOnly.class)) {
            dirty.set(true);
            c.remove(traits.backtracking);
        }
        return !dirty.get() ? null : traitses;
    }


    public static <S extends WantsZeroCopy> ByteBuffer bb(S b, UnaryOperator<ByteBuffer>... ops) {
        ByteBuffer b1 = b.asByteBuffer();
        for (int i = 0, opsLength = ops.length; i < opsLength; i++) {
            UnaryOperator<ByteBuffer> op = ops[i];
            if (null == op) {
                b1 = null;
                break;
            }
            b1 = op.apply(b1);
        }
        return b1;
    }

    public static <S extends WantsZeroCopy> ByteBufferReader fast(S zc) {
        return fast(zc.asByteBuffer());
    }

    public static ByteBufferReader fast(ByteBuffer buf) {
        ByteBufferReader r;
        try {
            if (buf.hasArray())
                r = new UnsafeHeapByteBufferReader(buf);
            else
                r = new UnsafeDirectByteBufferReader(buf);

        } catch (UnsupportedOperationException e) {
            r = new JavaByteBufferReader(buf);
        }
        return r;
    }

    /**
     * convenience method
     *
     * @param bytes
     * @param operations
     * @return
     */
    public static String str(ByteBuffer bytes, UnaryOperator<ByteBuffer>... operations) {
        ByteBuffer bb = bb(bytes, operations);
        return UTF_8.decode(bb).toString();
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @param atoms
     * @return
     */
    public static String str(WantsZeroCopy something, UnaryOperator<ByteBuffer>... atoms) {
        return str(something.asByteBuffer(), atoms);
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @param atoms
     * @return
     */
    public static String str(AtomicReference<? extends WantsZeroCopy> something, UnaryOperator<ByteBuffer>... atoms) {
        return str(something.get(), atoms);
    }

    /**
     * just saves a few chars
     *
     * @param something toString will run on this
     * @return
     */
    public static String str(Object something) {
        return String.valueOf(something);
    }

    /**
     * convenience method
     *
     * @param src
     * @param operations
     * @return
     */
    public static <T extends CharSequence> ByteBuffer bb(T src, UnaryOperator<ByteBuffer>... operations) {
        return bb(u8tf.c2b(String.valueOf(src)), operations);
    }

    public static ByteBuffer grow(ByteBuffer src) {
        return allocateDirect(src.capacity() << 1).put(src);
    }

    public static ByteBuffer cat(List<ByteBuffer> byteBuffers) {
        ByteBuffer[] byteBuffers1 = byteBuffers.toArray(new ByteBuffer[byteBuffers.size()]);
        return cat(byteBuffers1);
    }

    public static ByteBuffer cat(ByteBuffer... src) {
        ByteBuffer cursor;
        int total = 0;
        if (1 >= src.length) {
            cursor = src[0];
        } else {
            for (int i = 0, payloadLength = src.length; i < payloadLength; i++) {
                ByteBuffer byteBuffer = src[i];
                total += byteBuffer.remaining();
            }
            cursor = alloc(total);
            for (int i = 0, payloadLength = src.length; i < payloadLength; i++) {
                ByteBuffer byteBuffer = src[i];
                cursor.put(byteBuffer);
            }
            cursor.rewind();
        }
        return cursor;
    }

    public static ByteBuffer alloc(int size) {
        return null != getAllocator() ? getAllocator().allocate(size) : allocateDirect(size);
    }

    public static ByteBufferReader alloca(int size) {
        return fast(alloc(size));
    }

    public static ByteBuffer consumeString(ByteBuffer buffer) {
        //TODO unicode wat?
        while (buffer.hasRemaining()) {
            byte current = buffer.get();
            switch (current) {
                case '"':
                    return buffer;
                case '\\':
                    byte next = buffer.get();
                    switch (next) {
                        case 'u':
                            buffer.position(buffer.position() + 4);
                        default:
                    }
            }
        }
        return buffer;
    }

    public static ByteBuffer consumeNumber(ByteBuffer slice) {
        byte b = ((ByteBuffer) slice.mark()).get();

        boolean sign = '-' == b || '+' == b;
        if (!sign) {
            slice.reset();
        }

        boolean dot = false;
        boolean etoken = false;
        boolean esign = false;
        ByteBuffer r = null;
        while (slice.hasRemaining()) {
            while (slice.hasRemaining() && isDigit(b = ((ByteBuffer) slice.mark()).get())) ;
            switch (b) {
                case '.':
                    assert !dot : "extra dot";
                    dot = true;
                case 'E':
                case 'e':
                    assert !etoken : "missing digits or redundant exponent";
                    etoken = true;
                case '+':
                case '-':
                    assert !esign : "bad exponent sign";
                    esign = true;
                default:
                    if (!isDigit(b)) r = (ByteBuffer) slice.reset();
                    break;
            }
        }
        return r;
    }

    public static Allocator getAllocator() {
        return allocator;
    }

    public static void setAllocator(Allocator allocator) {
        std.allocator = allocator;
    }


}
======================================================
=== bbcursive/src/main/java/bbcursive/Cursive.java ===
======================================================
package bbcursive;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

import static bbcursive.std.bb;

/**
 * some kind of less painful way to do byteBuffer operations and a few new ones thrown in.
 * <p/>
 * evidence that this can be more terse than what jdk pre-8 allows:
 * <pre>
 *
 * res.add(bb(nextChunk, rewind));
 * res.add((ByteBuffer) nextChunk.rewind());
 *
 *
 * </pre>
 */
@FunctionalInterface
public interface Cursive extends UnaryOperator<ByteBuffer>{
  enum pre implements UnaryOperator<ByteBuffer> {
    duplicate {

      public ByteBuffer apply(ByteBuffer target) {
        return target.duplicate();
      }
    }, flip {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.flip();
      }
    }, slice {

      public ByteBuffer apply(ByteBuffer target) {
        return target.slice();
      }
    }, mark {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.mark();
      }
    }, reset {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.reset();
      }
    },
    /**
     * exists in both pre and post Cursive atoms.
     */
    rewind {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.rewind();
      }
    },
    /**
     * rewinds, dumps to console but returns unchanged buffer
     */
    debug {

      public ByteBuffer apply(ByteBuffer target) {
        System.err.println("%%: " + std.str(target, duplicate, rewind));
        return target;
      }
    }, ro {

      public ByteBuffer apply(ByteBuffer target) {
        return target.asReadOnlyBuffer();
      }
    },

    /**
     * perfoms get until non-ws returned.  then backtracks.by one.
     * <p/>
     * <p/>
     * resets position and throws BufferUnderFlow if runs out of space before success
     */


    forceSkipWs {
      public ByteBuffer apply(ByteBuffer target) {
        int position = target.position();

        while (target.hasRemaining() && Character.isWhitespace(target.get()));
        if (!target.hasRemaining()) {
          target.position(position);
          throw new BufferUnderflowException();
        }
        return bb(target, back1);
      }
    },
    skipWs {
      public ByteBuffer apply(ByteBuffer target) {
        boolean rem,captured = false;
        boolean r;
        while (rem=target.hasRemaining() && (captured|=(r=Character.isWhitespace( 0xff&((ByteBuffer) target.mark()).get())))&&r);
        return captured&&rem ? (ByteBuffer) target.reset() :captured?target:null;
      }
    },
    toWs {

      public ByteBuffer apply(ByteBuffer target) {
        while (target.hasRemaining() && !Character.isWhitespace(target.get())) {
        }
        return target;
      }
    },
    /**
     * @throws java.nio.BufferUnderflowException if EOL was not reached
     */
    forceToEol {

      public ByteBuffer apply(ByteBuffer target) {
        while (target.hasRemaining() && '\n' != target.get()) {
        }
        if (!target.hasRemaining()) {
          throw new BufferUnderflowException();
        }
        return target;
      }
    },
    /**
     * makes best-attempt at reaching eol or returns end of buffer
     */
    toEol {

      public ByteBuffer apply(ByteBuffer target) {
        while (target.hasRemaining() && '\n' != target.get()) { }
        return target;
      }
    },
    back1 {

      public ByteBuffer apply(ByteBuffer target) {
        int position = target.position();
        return ( ByteBuffer ) (0 < position ? target.position(position - 1) : target);
      }
    },
    /**
     * reverses position _up to_ 2.
     */
    back2 {

      public ByteBuffer apply(ByteBuffer target) {
        int position = target.position();
        return ( ByteBuffer ) (1 < position ? target.position(position - 2) : bb(target, back1));
      }
    }, /**
     * reduces the position of target until the character is non-white.
     */rtrim {

      public ByteBuffer apply(ByteBuffer target) {
        int start = target.position(), i = start;
        while (0 <= --i && Character.isWhitespace(target.get(i))) {
        }

        return ( ByteBuffer ) target.position(++i);
      }
    },

    /**
     * noop
     */
    noop {
      public ByteBuffer apply(ByteBuffer target) {
        return target;
      }
    }, skipDigits {

      public ByteBuffer apply(ByteBuffer target) {
        while (target.hasRemaining() && Character.isDigit(target.get())) {
        }
        return target;
      }
    }
  }

  enum post implements Cursive {
    compact {
      public ByteBuffer apply(ByteBuffer target) {
        return target.compact();
      }
    }, reset {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.reset();
      }
    }, rewind {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.rewind();
      }
    }, clear {

      public ByteBuffer apply(ByteBuffer target) {
        return ( ByteBuffer ) target.clear();
      }

    }, grow {

      public ByteBuffer apply(ByteBuffer target) {
        return std.grow(target);
      }

    }, ro {

      public ByteBuffer apply(ByteBuffer target) {
        return target.asReadOnlyBuffer();
      }
    },
    /**
     * fills remainder of buffer to 0's
     */
    pad0 {

      public ByteBuffer apply(ByteBuffer target) {
        while (target.hasRemaining()) {
          target.put((byte) 0);
        }
        return target;
      }
    },
    /**
     * fills prior bytes to current position with 0's
     */
    pad0Until {
      public ByteBuffer apply(ByteBuffer target) {
        int limit = target.limit();
        target.flip();
        while (target.hasRemaining()) {
          target.put((byte) 0);
        }
        return ( ByteBuffer ) target.limit(limit);
      }
    }
  }
}

=======================================================
=== bbcursive/src/main/java/bbcursive/lib/u8tf.java ===
=======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * unique code completion for utf8
 */
public enum u8tf {
    ;

    /**
     * utf8 encoder macro
     * @param charseq
     * @return
     */
    public static ByteBuffer c2b(String charseq) {
        return StandardCharsets.UTF_8.encode(charseq);
    }

    /**
     * UTF8 decoder macro
     *
     * @param buffer
     * @return defered  string translation decision
     */
    public static CharSequence b2c(ByteBuffer buffer) {
        return StandardCharsets.UTF_8.decode(buffer);
    }
}

=============================================================
=== bbcursive/src/main/java/bbcursive/lib/backtrack_.java ===
=============================================================
package bbcursive.lib;

import bbcursive.ann.Backtracking;
import bbcursive.std;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static bbcursive.std.bb;

@Backtracking
public enum backtrack_ {;

    @Backtracking
    public static UnaryOperator<ByteBuffer> backtracker(UnaryOperator<ByteBuffer>...allOf) {
        return new backTracker(allOf);

    }
    @Backtracking
    private static class backTracker implements UnaryOperator<ByteBuffer> {
        private final UnaryOperator<ByteBuffer>[] allOf;

        public backTracker(UnaryOperator<ByteBuffer>... allOf) {
            this.allOf = allOf;
        }

        @Override
        public String toString() {
            return "backtracker" + Arrays.deepToString(allOf);
        }


        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            std.flags.get().add(std.traits.skipper);

            return bb(buffer, allOf);
        }
    }
}

==========================================================
=== bbcursive/src/main/java/bbcursive/lib/advance.java ===
==========================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

/**
 * Created by jim on 1/17/16.
 */
public class advance {
    /**
     * consumes a token from the current ByteBuffer position.  null signals fail and should reset.
     *
     * @param exemplar ussually name().getBytes(), but might be other value also.
     * @return null if no match -- rollback not done here use Narsive.$ for whitespace and rollback
     */
    public static UnaryOperator<ByteBuffer> genericAdvance(byte... exemplar) {

        return new UnaryOperator<ByteBuffer>() {

              byte[] bytes=exemplar;

            @Override
            public String toString() {
                return asString();
            }


            public String asString() {
                bytes = exemplar;
                return  "advance->"+new String(bytes);
            }

            @Override
            public ByteBuffer apply(ByteBuffer target) {
                int c = 0;
                while (null != exemplar && null != target && target.hasRemaining() && c < exemplar.length && exemplar[c] == target.get())
                    c++;
                return !(null != target && c == exemplar.length) ? null : target;
            }
        };
    }
}

===========================================================
=== bbcursive/src/main/java/bbcursive/lib/skipper_.java ===
===========================================================
package bbcursive.lib;

import bbcursive.ann.Skipper;
import bbcursive.std;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static bbcursive.std.bb;

@Skipper
public interface skipper_ {;

    @Skipper
    static UnaryOperator<ByteBuffer> skipper(UnaryOperator<ByteBuffer>... allOf) {


        return new ByteBufferUnaryOperator(allOf);

    }

    @Skipper class ByteBufferUnaryOperator implements UnaryOperator<ByteBuffer> {
        private final UnaryOperator<ByteBuffer>[] allOf;

        public ByteBufferUnaryOperator(UnaryOperator<ByteBuffer>... allOf) {
            this.allOf = allOf;
        }

        @Override
        public String toString() {
            return "skipper"+ Arrays.deepToString(allOf);
        }


        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            std.flags.get().add(std.traits.skipper);

            return bb(buffer, allOf);
        }
    }
}

========================================================
=== bbcursive/src/main/java/bbcursive/lib/abort.java ===
========================================================
package bbcursive.lib;

import bbcursive.std;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

/**
 * Created by jim on 1/17/16.
 */
public class abort {
    public static UnaryOperator<ByteBuffer> abort(int rollbackPosition) {
        return b -> null == b ? null : std.bb(b, pos.pos(rollbackPosition), null);
    }
}

======================================================
=== bbcursive/src/main/java/bbcursive/lib/Int.java ===
======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;

/**
 * Created by jim on 1/17/16.
 */
public class Int {
    public static Integer parseInt(ByteBuffer r) {
        long x = 0;
        boolean neg = false;

        Integer res = null;
        if (r.hasRemaining()) {
            int i = r.get();
            switch (i) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    x = x * 10 + i - '0';
                    break;
                case '-':
                    neg = true;
                case '+':

            }
            while (r.hasRemaining()) {
                i = r.get();
                switch (i) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        x = x * 10 + i - '0';
                        break;
                    case '-':
                        neg = true;
                    case '+':
                        break;

                }
            }
            res = (int) ((neg ? -x : x) & 0xffffffffL);
        }
        return res;
    }

    public static Integer parseInt(String r) {
        long x = 0;
        boolean neg = false;

        Integer res = null;


        int length = r.length();
        if (0 < length) {
            int i = r.charAt(0);
            switch (i) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    x = x * 10 + i - '0';
                    break;
                case '-':
                    neg = true;
                case '+':
                    break;

            }

            for (int j = 1; j < length; j++) {
                i = r.charAt(i);
                switch (i) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        x = x * 10 + i - '0';
                        break;
                    case '-':
                        neg = true;
                    case '+':
                        break;
                }
            }


            res = (int) ((neg ? -x : x) & 0xffffffffL);
        }
        return res;
    }
}

=========================================================
=== bbcursive/src/main/java/bbcursive/lib/infix_.java ===
=========================================================
package bbcursive.lib;

import bbcursive.ann.Infix;
import bbcursive.std;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

public interface infix_ {;
@Infix
    public static UnaryOperator<ByteBuffer> infix(UnaryOperator<ByteBuffer>... allOf) {
    return new ByteBufferUnaryOperator(allOf);

}

    @Infix
class ByteBufferUnaryOperator implements UnaryOperator<ByteBuffer> {
        private final UnaryOperator<ByteBuffer>[] allOf;

        public ByteBufferUnaryOperator(UnaryOperator<ByteBuffer>... allOf) {
            this.allOf = allOf;
        }

        @Override
        public String toString() {
            return "infix"+ Arrays.deepToString(allOf);
        }

        @Override
        public ByteBuffer apply(ByteBuffer buffer) {

            return std.bb(buffer, allOf);
        }
    }
}

==========================================================
=== bbcursive/src/main/java/bbcursive/lib/confix_.java ===
==========================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static bbcursive.lib.allOf_.allOf;
import static bbcursive.std.bb;

/**
 * Created by jim on 1/17/16.
 */
public class confix_ {
    public static UnaryOperator<ByteBuffer> confix(UnaryOperator<ByteBuffer> operator, char... chars) {
        return new UnaryOperator<ByteBuffer>() {
            @Override
            public String toString() {
                return "confix_:"+ Arrays.toString(chars)+" : "+operator;
            }

            @Override
            public ByteBuffer apply(ByteBuffer buffer) {
                UnaryOperator<ByteBuffer> chlit = chlit_.chlit(chars[0]);
                char aChar = chars[2 > chars.length ? 0 : 1];
                UnaryOperator<ByteBuffer> chlit1 = chlit_.chlit(aChar);
                return bb(buffer,confix(chlit, chlit1, operator));
            }
        };
    }
 public    static UnaryOperator<ByteBuffer> confix(UnaryOperator<ByteBuffer> before, UnaryOperator<ByteBuffer> after, UnaryOperator<ByteBuffer> operator) {

     return new UnaryOperator<ByteBuffer>() {

         @Override
         public String toString() {
             return "confix"+Arrays.deepToString(new UnaryOperator[]{before, operator, after});
         }

         @Override
         public ByteBuffer apply(ByteBuffer buffer) {
             return bb(buffer,allOf(before, operator, after));
         }
     };
    }

    public static UnaryOperator<ByteBuffer> confix(char open, UnaryOperator<ByteBuffer> unaryOperator, char close) {
        return confix(unaryOperator, open, close);
    }

    public static UnaryOperator<ByteBuffer> confix(String s, UnaryOperator<ByteBuffer> unaryOperator) {
        return confix(unaryOperator, s.toCharArray());
    }

}
;

=========================================================
=== bbcursive/src/main/java/bbcursive/lib/allOf_.java ===
=========================================================
package bbcursive.lib;

import bbcursive.std;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

import static java.util.Arrays.deepToString;

/**
 * Created by jim on 1/17/16.
 */
public interface  allOf_ {
    ;

    /**
     * bbcursive.lib.allOf_ of, in sequence, without failures
     *
     * @param allOf
     * @return null if not bbcursive.lib.allOf_ match in sequence
     */
    static UnaryOperator<ByteBuffer> allOf(UnaryOperator<ByteBuffer>... allOf) {
        return new UnaryOperator<ByteBuffer>() {
            @Override
            public String toString() {
                return "all"+ deepToString(allOf);
            }

            @Override
            public ByteBuffer apply(ByteBuffer target) {
                return   std.bb(target, allOf);
            }
        };
    }
}

=======================================================
=== bbcursive/src/main/java/bbcursive/lib/opt_.java ===
=======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static bbcursive.std.bb;

/**
 * Created by jim on 1/17/16.
 */
public enum opt_ {
    ;

    public static UnaryOperator<ByteBuffer> opt(UnaryOperator<ByteBuffer>... unaryOperators) {
        return new ByteBufferUnaryOperator(unaryOperators);
    }

    public static class ByteBufferUnaryOperator implements UnaryOperator<ByteBuffer> {
        private UnaryOperator<ByteBuffer>[] allOrPrevious;

        @Override
        public String toString() {
            return "opt:" + Arrays.deepToString(allOrPrevious);
        }

        public ByteBufferUnaryOperator(UnaryOperator<ByteBuffer>[] allOrPrevious) {

            this.allOrPrevious = allOrPrevious;
        }

        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            int position = buffer.position();
            ByteBuffer r = bb(buffer, allOrPrevious);
            if (null == r) {
                buffer.position(position);
            }
            return buffer;
        }
    }
}

=======================================================
=== bbcursive/src/main/java/bbcursive/lib/push.java ===
=======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;

/**
 * Created by jim on 1/17/16.
 */
public class push {
    /**
     * @param src
     * @param dest
     * @return
     */

    public static ByteBuffer push(ByteBuffer src, ByteBuffer dest) {
        int need = src
                .remaining(),
                have = dest.remaining();
        if (have > need) {
            return dest.put(src);
        }
        dest.put((ByteBuffer) src.slice().limit(have));
        src.position(src.position() + have);
        return dest;
    }
}

==========================================================
=== bbcursive/src/main/java/bbcursive/lib/repeat_.java ===
==========================================================
package bbcursive.lib;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static bbcursive.std.bb;

/**
 * Created by jim on 1/17/16.
 */
public enum repeat_ {
    ;

    @NotNull
    public static UnaryOperator<ByteBuffer> repeat(UnaryOperator<ByteBuffer>... op) {
         return new UnaryOperator<ByteBuffer>() {


            public String toString() {
                return "rep:"+ Arrays.deepToString(op);
            }

            @Override
            public ByteBuffer apply(ByteBuffer byteBuffer) {
                int mark = byteBuffer.position();
                int matches = 0;
                ByteBuffer handle = byteBuffer;
                ByteBuffer last = null;
                while (handle.hasRemaining()) {
                    last = handle;
    //                if (null != (handle=op.apply(handle))) {
                    if (null != (handle=bb(last,op))){
                        matches++;
                        mark = handle.position();
                    }else break;
                }

                if (matches > 0 && last.hasRemaining())
                    last.position(mark);

                return matches > 0 ? last: null;
            }
        };
    }

}




=========================================================
=== bbcursive/src/main/java/bbcursive/lib/strlit.java ===
=========================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.function.UnaryOperator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by jim on 1/17/16.
 */
public class strlit {

    public static UnaryOperator<ByteBuffer> strlit(CharSequence s) {
        return new ByteBufferUnaryOperator(s);
    }

    private static class ByteBufferUnaryOperator implements UnaryOperator<ByteBuffer> {
        private final CharSequence s;

        public ByteBufferUnaryOperator(CharSequence s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return MessageFormat.format("u8\"{0}\"", s);
        }

        @Override
        public ByteBuffer apply(ByteBuffer buffer) {
            ByteBuffer encode = UTF_8.encode(String.valueOf(s));
            while (encode.hasRemaining() && buffer.hasRemaining() && encode.get() == buffer.get()) ;
            return encode.hasRemaining() ? null : buffer;
        }
    }
}

======================================================
=== bbcursive/src/main/java/bbcursive/lib/log.java ===
======================================================
package bbcursive.lib;

import bbcursive.WantsZeroCopy;
import bbcursive.std;

import java.nio.ByteBuffer;

import static bbcursive.Cursive.pre.debug;

/**
 * Created by jim on 1/17/16.
 */
public class log {
    /**
     * conditional debug output assert log(Object,[prefix[,suffix]])
     *
     * @param ob
     * @param prefixSuffix
     * @return
     */
    public static void log(Object ob, String... prefixSuffix) {
        assert log$(ob, prefixSuffix);
    }

    /**
     * conditional debug output assert log(Object,[prefix[,suffix]])
     *
     * @param ob
     * @param prefixSuffix
     * @return
     */
    public static boolean log$(Object ob, String...prefixSuffix) {
        boolean hasSuffix = 1 < prefixSuffix.length;
        if (0 < prefixSuffix.length)
            System.err.print(prefixSuffix[0] + "\t");
        if (!(ob instanceof ByteBuffer)) {
            if (ob instanceof WantsZeroCopy) {
                WantsZeroCopy wantsZeroCopy = (WantsZeroCopy) ob;
                std.bb(wantsZeroCopy.asByteBuffer(), debug);
            } else {
                std.bb(String.valueOf(ob), debug);
            }
        } else {
            std.bb((ByteBuffer) ob, debug);
        }
        if (hasSuffix) {
            System.err.println(prefixSuffix[1] + "\t");
        }
        return true;
    }
}

======================================================
=== bbcursive/src/main/java/bbcursive/lib/lim.java ===
======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

/**
 * Created by jim on 1/17/16.
 */
public class lim implements UnaryOperator<ByteBuffer> {
    private final int position;

    public lim(int position) {
        this.position = position;
    }

    /**
     * reposition
     *
     * @param position
     * @return
     */
    public static UnaryOperator<ByteBuffer> lim(int position) {
        return new lim(position);

    }

    @Override
    public ByteBuffer apply(ByteBuffer target) {
        return (ByteBuffer) target.limit(position);
    }
}

=========================================================
=== bbcursive/src/main/java/bbcursive/lib/anyOf_.java ===
=========================================================
package bbcursive.lib;

import bbcursive.Cursive.pre;
import bbcursive.ann.Backtracking;
import bbcursive.vtables._edge;
import bbcursive.vtables._ptr;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static bbcursive.std.*;
import static java.util.Arrays.binarySearch;
import static java.util.Arrays.deepToString;

/**
 * Created by jim on 1/17/16.
 */
public class anyOf_ {

    public static final EnumSet<traits> NONE_OF = EnumSet.noneOf(traits.class);

    public static UnaryOperator<ByteBuffer> anyOf(UnaryOperator<ByteBuffer>... anyOf) {


        return new UnaryOperator<ByteBuffer>() {

            @Override
            public String toString() {
                return "any" + deepToString(anyOf);
            }

            @Override
            public ByteBuffer apply(ByteBuffer buffer) {
                int mark = buffer.position();
                if (flags.get().contains(traits.skipper)) {
                    ByteBuffer apply = pre.skipWs.apply(buffer);
                    buffer = apply == null ? (ByteBuffer) buffer.position(mark) : apply;
                    if (!buffer.hasRemaining()) {
                        return null;
                    }
                }
                mark = buffer.position();
                int[] offsets = {mark, mark};
                Set[] flaggs = {NONE_OF};


                ByteBuffer[] r = {null};
                final ByteBuffer[] finalBuffer = {buffer};

                Arrays.stream(anyOf)/*.parallel()*/
                        .map((Function<UnaryOperator<ByteBuffer>, _edge<_edge<Set<traits>,
                                _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>>)
                                op -> new _edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>() {
                                    private final ByteBuffer buffer = finalBuffer[0];

                                    @Override
                                    protected _ptr at() {
                                        return r$();
                                    }

                                    @Override
                                    protected _ptr goTo(_ptr ptr) {
                                        throw new Error("trifling with an immutable pointer");
                                    }

                                    /**
                                     * this binds a pointer to a pair of ByteBuffer and Integer.  note the bytebuffer
                                     * is mutated by this operation and will corrupt the source stream if this isn't
                                     * a slice or a duplicate
                                     *
                                     * @return the _ptr
                                     */
                                    @Override

                                    protected _ptr r$() {

                                        return (_ptr) new _ptr().bind(
                                                (ByteBuffer) buffer.duplicate().position(offsets[1]), offsets[0]);
                                    }

                                    @Override
                                    public _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>> core(_edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>... e) {
                                        return new _edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>() {
                                            @Override
                                            public Set<traits> core(_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>... e) {
                                                return flaggs[0];
                                            }

                                            @Override
                                            protected _edge<UnaryOperator<ByteBuffer>, Integer> at() {
                                                return r$();
                                            }

                                            @Override
                                            protected _edge<UnaryOperator<ByteBuffer>, Integer> goTo(_edge<UnaryOperator<ByteBuffer>, Integer> unaryOperatorInteger_edge) {
                                                throw new Error("cant move this");
                                            }

                                            @Override
                                            protected _edge<UnaryOperator<ByteBuffer>, Integer> r$() {
                                                return new _edge<UnaryOperator<ByteBuffer>, Integer>() {
                                                    @Override
                                                    protected Integer at() {
                                                        return r$();
                                                    }

                                                    @Override
                                                    protected Integer goTo(Integer integer) {
                                                        throw new Error("immutable");
                                                    }

                                                    @Override
                                                    public UnaryOperator<ByteBuffer> core(_edge<UnaryOperator<ByteBuffer>, Integer>... e) {
                                                        return op;
                                                    }

                                                    @Override
                                                    protected Integer r$() {
                                                        return offsets[1];
                                                    }
                                                };
                                            }
                                        };
                                    }
                                }).filter(
                        ed -> {
                            UnaryOperator<ByteBuffer> op = ed.core().location().core();
                            Integer newPosition = ed.location().location();
                            ByteBuffer byteBuffer = (ByteBuffer) ed.location().core().duplicate().position(newPosition);
                            ByteBuffer res = op.apply(byteBuffer);
                            if (null != res) {
                                offsets[1] = res.position();
                                flaggs[0] = EnumSet.copyOf(flags.get());
                                return true;
                            }
                            return false;
                        })
                        .findFirst().ifPresent(
                        edge_ptr_edge -> {
                            Consumer<_edge<_edge<Set<traits>, _edge<UnaryOperator<ByteBuffer>, Integer>>, _ptr>> edgeConsumer = outbox.get();
                            edgeConsumer.accept(edge_ptr_edge);
                            r[0] = (ByteBuffer) finalBuffer[0].position(offsets[1]);
                        });

                return r[0];
            }
        };
    }


    @Backtracking
    public static UnaryOperator<ByteBuffer> anyIn(CharSequence s) {
        int[] ints = s.chars().sorted().toArray();
        return new UnaryOperator<ByteBuffer>() {
            @Override
            public String toString() {
                StringBuilder b=new StringBuilder();
                IntStream.of(ints).forEach(i -> b.append((char) (i & 0xffff)));
                return "in"+Arrays.deepToString(new String[]{b.toString()});
            }

            @Override
            public ByteBuffer apply(ByteBuffer b) {
                ByteBuffer r = null;
                if (null != b && b.hasRemaining()) {
                    byte b1 = b.get();
                    if (-1 < binarySearch(ints, b1 & 0xff))
                        r = b;
                }
                return r;
            }
        };
    }
}


=========================================================
=== bbcursive/src/main/java/bbcursive/lib/value_.java ===
=========================================================
package bbcursive.lib;

import bbcursive.ann.ForwardOnly;
import bbcursive.ann.Infix;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

import static bbcursive.lib.chlit_.chlit;
import static bbcursive.lib.infix_.infix;
import static bbcursive.lib.opt_.opt;
import static bbcursive.lib.repeat_.repeat;

/**
 * Created by jim on 1/21/16.
 */
@Infix
@ForwardOnly
public class value_ implements UnaryOperator<ByteBuffer> {

    public static final value_ VALUE_ = new value_();

    private value_() {
    }

    public static value_ value() {
        return VALUE_;
    }

    @Override
    public ByteBuffer apply(ByteBuffer buffer) {
        return (ByteBuffer) infix(opt(chlit("0")), anyOf_.anyIn("1.0"), opt(repeat(anyOf_.anyIn("1029384756"))));
    }
}

===============================================================
=== bbcursive/src/main/java/bbcursive/lib/package-info.java ===
===============================================================
/**
 * these objects in here are almost all very elegant as lambdas in an enum or static in an interface HOWEVER,
 * they are here in individual java source files for various reasons:
 * <ol>
 * <li>parser traits via annotations and/or marker interfaces</li>
 * <li>cheapest informative toString</li>
 * <li>passing by enum confuses clinit and/or unknown pure-evil inits in my source code</li>
 * </ol>
 */
package bbcursive.lib;



=========================================================
=== bbcursive/src/main/java/bbcursive/lib/chlit_.java ===
=========================================================
package bbcursive.lib;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

/**
char literal
 */
public class chlit_ {
    public static UnaryOperator<ByteBuffer> chlit(char c) {
        return new ByteBufferUnaryOperator(c);
    }

    public static UnaryOperator<ByteBuffer> chlit(CharSequence s) {
        return chlit(s.charAt(0));
    }


    private static class ByteBufferUnaryOperator implements UnaryOperator<ByteBuffer> {
        private final char c;

        public ByteBufferUnaryOperator(char c) {
            this.c = c;
        }

        @Override
        public String toString() {
            return "c8'" +
                    c+"'";
        }

        @Nullable
        @Override
        public ByteBuffer apply(ByteBuffer buf) {
            if (null == buf) {
                return null;
            }
            if (buf.hasRemaining()) {
                byte b = buf.get();
                return (c & 0xff) == (b & 0xff) ? buf : null;
            }
            return null;



        }
    }
}

======================================================
=== bbcursive/src/main/java/bbcursive/lib/pos.java ===
======================================================
package bbcursive.lib;

import java.nio.ByteBuffer;
import java.util.function.UnaryOperator;

/**
 * Created by jim on 1/17/16.
 */
public class pos implements UnaryOperator<ByteBuffer> {
    private final int position;

    public pos(int position) {
        this.position = position;
    }

    /**
     * reposition
     *
     * @param position
     * @return
     */
    public static UnaryOperator<ByteBuffer> pos(int position) {
        return new pos(position){
            @Override
            public String toString() {
                return "pos("+position+")";
            }
        };
    }

    @Override
    public ByteBuffer apply(ByteBuffer t) {
        return null == t ? t : (ByteBuffer) t.position(position);
    }
}

============================================================
=== bbcursive/src/main/java/bbcursive/WantsZeroCopy.java ===
============================================================
package bbcursive;

import java.nio.ByteBuffer;

/**
 * Created by jim on 8/8/14.
 */
public interface WantsZeroCopy {
  ByteBuffer asByteBuffer();
}

