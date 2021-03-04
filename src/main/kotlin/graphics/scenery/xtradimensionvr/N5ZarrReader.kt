package graphics.scenery.xtradimensionvr

import net.imglib2.Cursor
import net.imglib2.Interval
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.NativeType
import net.imglib2.type.numeric.integer.ByteType
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.Views
import net.imglib2.view.Views.interval
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader


class N5ZarrReader {
    init {
        val n5 = N5ZarrReader("datasets/array.zarr")
        val zDataset = N5Utils.open<FloatType>(n5, "/X")
        val dType = n5.getDatasetAttributes("/X").dataType
        println(dType)
        println(zDataset)

        val hyperView = Views.flatIterable(Views.hyperSlice(zDataset, 1, 2637))
        val cursor: Cursor<FloatType> = hyperView.cursor()
        while (cursor.hasNext()) {
            val t = cursor.next()
            val x = cursor.getLongPosition(0)
            //val y = cursor.getLongPosition(1)
            print(x)
            print(":")
            println(t)
        }



    }



}

fun main(){
    N5ZarrReader()
}