package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.base.mdarray.MDFloatArray
import ch.systemsx.cisd.base.mdarray.MDIntArray
import ch.systemsx.cisd.hdf5.*


class SparseReader(pathName: String) {
    private val reader: IHDF5Reader = HDF5Factory.openForReading(pathName)

    private val csrData: MDFloatArray = reader.float32().readMDArray("/X/data")
    private val csrIndices: MDIntArray = reader.int32().readMDArray("/X/indices")
    private val csrIndptr: MDIntArray = reader.int32().readMDArray("/X/indptr")

    private val cscData: MDFloatArray = reader.float32().readMDArray("/layers/X_csc/data")
    private val cscIndices: MDIntArray = reader.int32().readMDArray("/layers/X_csc/indices")
    private val cscIndptr: MDIntArray = reader.int32().readMDArray("/layers/X_csc/indptr")

    private val numGenes = reader.string().readArrayRaw("/var/index").size
    private val numCells = reader.string().readArrayRaw("/obs/index").size

    /**
     * return dense row of gene expression values for a chosen row / cell
     */
    fun csrReader(row: CellIndex = 0): FloatArray {
        // init float array of zeros the length of the number of genes
        val exprArray = FloatArray(numGenes)

        // slice pointer array to give the number of non-zeros in the row
        val start = csrIndptr[row]
        val end = csrIndptr[row+1]

        // from start index until (excluding) end index, substitute non-zero values into empty array
        for(i in start until end){
            exprArray[csrIndices[i]] = csrData[i]
        }
        return exprArray
    }

    /**
     * return dense column of gene expression values for a chosen column / gene
     */
    fun cscReader(col: GeneIndex = 0): FloatArray {
        val exprArray = FloatArray(numCells)

        val start = cscIndptr[col]
        val end = cscIndptr[col+1]

        for(i in start until end){
            exprArray[cscIndices[i]] = cscData[i]
        }
        return exprArray
    }
}


fun main(){
    SparseReader("/home/luke/PycharmProjects/VRCaller/file_conversion/tabula_vr_processed.h5ad")
}