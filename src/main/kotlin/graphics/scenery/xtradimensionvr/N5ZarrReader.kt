package graphics.scenery.xtradimensionvr

import net.imglib2.Cursor
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.type.numeric.integer.*
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.Views
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader
import kotlin.reflect.typeOf


class N5ZarrReader {
    init {
        val zarrPath = "/home/luke/PycharmProjects/VRCaller/file_conversion/kidney.zarr"
        val csvRoot = "/home/luke/PycharmProjects/VRCaller/file_conversion/test_export"

        val n5 = N5ZarrReader(zarrPath)
        val valArray = openDataset(n5, "/X/data")
        val rowIndex = openDataset(n5, "/X/indices")
        val colPointer = openDataset(n5, "/X/indptr")

        println(n5.getDatasetAttributes("/X/data").dataType)

        val ArrayIterable = Views.flatIterable(valArray)
        var forCounter = 0
        for(i in valArray){
            if(forCounter<100) {
                println(i)
            }
            else{
                break
            }
            forCounter += 1
        }



    }

    fun openDataset(reader: N5ZarrReader, dataset: String): CachedCellImg<*,*>{
        reader.getDatasetAttributes(dataset).dataType.let {
            return when (it) {
                DataType.INT8 -> N5Utils.open<ByteType>(reader, dataset)
                DataType.INT16 -> N5Utils.open<ShortType>(reader, dataset)
                DataType.INT32 -> N5Utils.open<IntType>(reader, dataset)
                DataType.INT64 -> N5Utils.open<LongType>(reader, dataset)
                DataType.UINT8 -> N5Utils.open<UnsignedByteType>(reader, dataset)
                DataType.UINT16 -> N5Utils.open<UnsignedShortType>(reader, dataset)
                DataType.UINT32 -> N5Utils.open<UnsignedIntType>(reader, dataset)
                DataType.UINT64 -> N5Utils.open<UnsignedLongType>(reader, dataset)
                DataType.FLOAT32 -> N5Utils.open<FloatType>(reader, dataset)
                DataType.FLOAT64 -> N5Utils.open<DoubleType>(reader, dataset)
                else -> error("Unsupported DataType: $it")
            }
        }

    }
}


fun main(){
    N5ZarrReader()
}