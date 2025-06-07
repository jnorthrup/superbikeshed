

kscript - test2.fastq <<"EOF"
java.io.File(args[0]).useLines {
    it.map {
        if (!it.startsWith(">")) it else "huhu" + it
    }.forEach { println(it) }
}
EOF




kscript - <<"EOF"
println("getting started")
generateSequence() { readLine() }.map {
    if (!it.startsWith(">")) it else
        "huhu" + it
}.forEach { println(it) }
EOF


head -n 1000 test.fastq > test2.fastq


## how use stdin but still use heredoc for cod
kscript - test2.fastq <(echo <<"EOF"
println("getting started")
//generateSequence() { readLine() }.map {
File(args[0]).readLines().map {
    if (!it.startsWith(">")) it else
        "huhu" + it
}.forEach { println(it) }
EOF
)



## todo provide picard tools examples
http://mvnrepository.com/artifact/com.github.broadinstitute/picard/2.5.0
potentially like
https://www.biostars.org/p/52698/


## simplified line streaming



kscript -s '
//DEPS de.mpicbg.scicomp:kutils:0.2-SNAPSHOT
kutils.KscriptHelpers.processStdin { "huhu" + it }

'

## todo streamline further by using simple code wrapper for (see https://github.com/holgerbrandl/kscript/issues/9)
kscript -st '"huhu" + it'


## REPL test: Filter-Join fasta files by ID
kotlinc -classpath $(resdeps.kts de.mpicbg.scicomp:kutils:0.2)
<<"EOF"
import de.mpicbg.scicomp.kscript.*

"house\nasdf".processLines { "huhu" + it }

EOF