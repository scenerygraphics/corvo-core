package graphics.scenery.xtradimensionvr

import com.bc.zarr.ZarrArray
import com.bc.zarr.ArrayParams
import com.bc.zarr.DataType
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext
import org.lwjgl.system.CallbackI
import org.nd4j.linalg.api.buffer.DataBuffer
import org.nd4j.linalg.cpu.nativecpu.buffer.Int8Buffer
import org.nd4j.linalg.factory.Nd4j
import ucar.nc2.ft.point.standard.JoinArray
import java.util.*


class ZarrReader(val path: String) {
    init {
        // my dataset
        val jzarr_array = ZarrArray.open("datasets/array.zarr/X")

        //val shape = jzarr_array.shape
        val shape = intArrayOf(2638, 1838)
        val read_shape = intArrayOf(2638, 1)
        val offset = intArrayOf(0, 0)

        // query datatype of dataset
        val datatype = jzarr_array.dataType.toString()
        println(datatype)

        val asDarrayItem = jzarr_array.read() as FloatArray
        print(asDarrayItem.toList()) // data
        //print(asDarrayItem) // jvm object
        //print(jzarr_array) // info

        // i1 - Btye, i2 - Short, i4 - Int, i8 - Long, f4 - Float, f8 - Double
        //|O appears to be either a python object or a pandas dataframe - un-openable





    }
}

fun main(){
    ZarrReader("/obs/louvain")
}