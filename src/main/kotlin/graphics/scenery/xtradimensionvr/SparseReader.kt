package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.hdf5.*


class SparseReader {
    val pathName = "/home/luke/PycharmProjects/VRCaller/file_conversion/liver_vr_processed.h5ad"
    val csvPathName = "/home/luke/PycharmProjects/VRCaller/file_conversion/liver_annotations"
    val reader = HDF5Factory.openForReading(pathName)

    fun csrReader(row: Int = 0): FloatArray {
        // return dense row of gene expression values for a chosen row / cell

        val data = reader.float32().readMDArray("/X/data")
        val indices = reader.int32().readMDArray("/X/indices")
        val indptr = reader.int32().readMDArray("/X/indptr")

        val exprArray = FloatArray(reader.string().readArrayRaw("/var/index").size)

        val start = indptr[row]
        val end = indptr[row+1]

        for(i in start until end){
            exprArray[indices[i]] = data[i]
        }

        return exprArray
    }

    fun cscReader(col: Int = 0): FloatArray {
        // return dense column of gene expression values for a chosen column / gene

        val data = reader.float32().readMDArray("/layers/X_csc/data")
        val indices = reader.int32().readMDArray("/layers/X_csc/indices")
        val indptr = reader.int32().readMDArray("/layers/X_csc/indptr")

        val exprArray = FloatArray(reader.string().readArrayRaw("/obs/index").size)

        val start = indptr[col]
        val end = indptr[col+1]

        for(i in start until end){
            exprArray[indices[i]] = data[i]
        }

        return exprArray
    }
}


fun main(){
//    SparseReader().csrReader(2858) // final row is size(index)-1. Note size(indptr) = size(index) + 1 -> final row = size(index) -2
//    SparseReader().cscReader(22965)  //22965 is final column = obs index - 1
}