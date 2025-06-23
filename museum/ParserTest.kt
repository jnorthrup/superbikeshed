import kotlin.test.*
import java.io.*

class ParserTest {
    /**
     * Expands a brace expression using the BashBraceParser.
     */
    private fun expandWithParser(expr: String): List<String> =
        parse.bash.BashBraceParser.of(expr).parse().sorted()

    /**
     * Expands a brace expression using bash itself.
     */
    private fun expandWithBash(expr: String): List<String> {
        val command = arrayOf("bash", "-c", "echo $expr")
        val proc = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        return output.split(" ").filter { it.isNotEmpty() }.sorted()
    }

    @Test
    fun testSimpleBraceExpansion() {
        val expr = "file{1,2,3}.txt"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }

    @Test
    fun testNumericSequence() {
        val expr = "item{5..7}"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }

    @Test
    fun testCharSequence() {
        val expr = "a{b..d}z"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }

    @Test
    fun testListWithPrefixSuffix() {
        val expr = "pre{a,b,c}post"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }

    @Test
    fun testSingleValue() {
        val expr = "foo{bar}baz"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }

    @Test
    fun testComplexBashArrayExpansion() {
        val expr = "{{md5,sha{2{24,56},384,512}}sum,locate,logname,sh{uf,red}} {c{u,a}t,ls,exp{r,and},{z,tc,c,ba,}sh,mk{dir,fifo,nod,sock}} {cp,rm{,dir},stri{p,ngs},ln,rsync,mv,se{q,d},grep} {awk,tr{,ue},false,d{d,b,f,u},paste,xargs,utmp,screen} {read{link,elf},{u,dir,base}name,base64,find,make,head,tail} {join,kill{,all},m4,{,q,t}sort,{,s,w}diff,patch,unlink,yes} {zcat,env,dir{,colors,name},print{,f,env},who{,ami,is},pwd} {join,ch{root,own,mod,sh},makedep,awk,ar,ld,echo,which,wc} {svn,git,cvs,java{,c,p,w},jre,join,te{st,e},groups,head,vdir} {split,id,wait,sleep,sync}"
        val parserResult = expandWithParser(expr)
        val bashResult = expandWithBash(expr)
        assertEquals(bashResult, parserResult)
    }
} 