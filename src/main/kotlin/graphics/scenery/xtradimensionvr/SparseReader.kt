package graphics.scenery.xtradimensionvr
import ch.systemsx.cisd.hdf5.*


class SparseReader {
    val pathName = "/home/luke/PycharmProjects/VRCaller/file_conversion/tabula_vr_processed.h5ad"

    init {
        val reader = HDF5Factory.openForReading(pathName)

        val notCat = reader.getDataSetInformation("/obs/cell_ontology_class")
        println(notCat)
    }

    fun csrReader(row: Cell = 0): FloatArray {
        val reader = HDF5Factory.openForReading(pathName)
        // return dense row of gene expression values for a chosen row / cell

        val data = reader.float32().readMDArray("/X/data")
        val indices = reader.int32().readMDArray("/X/indices")
        val indptr = reader.int32().readMDArray("/X/indptr")

        // init float array of zeros the length of the number of cells
        val exprArray = FloatArray(reader.string().readArrayRaw("/var/index").size)

        // slice pointer array to give the number of non-zeros in the row
        val start = indptr[row]
        val end = indptr[row+1]

        // from stat index until (but excluding) end index, substitute non-zero values into empty array
        for(i in start until end){
            exprArray[indices[i]] = data[i]
        }
        reader.close()
        return exprArray
    }

    fun cscReader(col: Gene = 0): FloatArray {
        val reader = HDF5Factory.openForReading(pathName)
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
        reader.close()
        return exprArray
    }
}


fun main(){
    SparseReader()
}