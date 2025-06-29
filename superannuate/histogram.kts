import cursors.TypeMemento
import cursors.*
import cursors.context.Columnar
import cursors.context.NioMMap
import cursors.context.RowMajor
import cursors.get
import cursors.groupClusters
import cursors.io.*
import cursors.io.IOMemento.*
import vec.macros.*
import vec.util._a
import java.nio.file.Paths

println(1)

val python = """
import pandas as pd;
d1names=['SalesNo', 'SalesAreaID', 'date', 'PluNo', 'ItemName', 'Quantity', 'Amount', 'TransMode']
d1=pd.read_fwf('superannuated1909.fwf',
    names=d1names,
    colspecs=[(0, 11), (11, 15), (15, 25), (25, 40), (40, 60), (60, 82), (82, 103), (103, 108)])
for i in d1names: print( (i,len( d1[i].unique() )) )
"""
println("emulating " + python)

val rowFwfFname = Paths.get(".", "superannuated1909.fwf")
val coords = intArrayOf(
    0, 11, 11, 15, 15, 25, 25, 40, 40, 60, 60, 82, 82, 103, 103, 108
).zipWithNext()

val meta: Vect02<IOMemento, String?> = vect0rOf(
    IoString t2 "SalesNo",
    IoString t2 "SalesAreaID",
    IoLocalDate t2 "date",
    IoString t2 "PluNo",
    IoString t2 "ItemName",
    IoFloat t2 "Quantity",
    IoFloat t2 "Amount",
    IoString t2 "TransMode"
)

val columnar = Columnar(meta as Vect02<TypeMemento, String?>)
val nioMMap = NioMMap(MappedFile(rowFwfFname.toString()), NioMMap.text(columnar.left))
val fixedWidth = RowMajor.fixedWidthOf(nioMMap, coords)
val indexable = RowMajor.indexableOf(nioMMap, fixedWidth)
val curs1 = cursorOf(
    RowMajor().fromFwf(
        fixedWidth,
        indexable,
        nioMMap,
        columnar
    )
)
val names = meta.right.toList()


Runtime.getRuntime().maxMemory()

names.forEachIndexed { index: Int, s: String? ->
    println(s to curs1.groupClusters(_a[index]).size)
}

val transmode=sequence  {
    val column = curs1[names.indices.last()]
    for(ix in 0 until column.size) {
        val r:RowVec = column at ix
        val v=r.left[0]
        yield(v)
    }
}
val message = transmode.distinct().toList()
println(message)