package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.hdf5.HDF5Factory
import graphics.scenery.numerics.Random
import hdf.hdf5lib.exceptions.HDF5SymbolTableException
import java.util.*
import kotlin.collections.ArrayList

class AnnotationsIngest {
    private val h5adPath = "/home/luke/PycharmProjects/VRCaller/file_conversion/tabula_vr_processed.h5ad"

    fun fetchGeneExpression(nameOutput:ArrayList<String>, lazyNameOutput: ArrayList<String> = arrayListOf(), lazy: Boolean = false): ArrayList<FloatArray> {
        val nameReader = h5adAnnotationReader("/var/index")
        val geneIndexList = ArrayList<Int>()

        val randGeneList = ArrayList<String>()
        for(i in 0..12){
            randGeneList.add(nameReader[Random.randomFromRange(0f, nameReader.size.toFloat()).toInt()] as String)
//        randGeneList.add(nameReader[24] as String)
        }
        if (!lazy) {
            for (i in randGeneList) {
                nameOutput.add(i)
                geneIndexList.add(nameReader.indexOf(i))
            }
        }
        else {
            for (i in randGeneList) {
                lazyNameOutput.add(i)
                geneIndexList.add(nameReader.indexOf(i))
            }
        }

        val geneReader = SparseReader()
        val geneExpression = ArrayList<FloatArray>()
        for(i in geneIndexList){
            geneExpression.add(geneReader.cscReader(i))
        }
        return geneExpression
    }

    fun UMAPReader3D(): ArrayList<ArrayList<Float>>{

        val reader = HDF5Factory.openForReading(h5adPath)
        val UMAP = arrayListOf<ArrayList<Float>>()

        var tripletCounter = 0
        val cellUMAP = ArrayList<Float>()

        for(coordinate in reader.float32().readArray("/obsm/X_umap")){
            if(tripletCounter < 3){
                cellUMAP.add(coordinate)
                tripletCounter += 1
            }
            else {
                UMAP.add(arrayListOf(cellUMAP[0], cellUMAP[1], cellUMAP[2])) // actual values instead of pointer to object
                cellUMAP.clear()
                tripletCounter = 1 // zero for first loop, then 1, as first entry is added in else clause
                cellUMAP.add(coordinate)
            }
        }
        UMAP.add(arrayListOf(cellUMAP[0], cellUMAP[1], cellUMAP[2])) // add final sub-array

        reader.close()
        return UMAP
    }

    fun h5adAnnotationReader(hdfPath: String, asString: Boolean = true): ArrayList<Any>{
        /**
         * reads any 1 dimensional annotation (ie obs, var, uns), checking if a categorical map exists for them
         **/
        if(hdfPath[4].toString() != "/"){
            throw InputMismatchException("this function is only for reading obs, var, and uns")
            }

        val reader = HDF5Factory.openForReading(h5adPath)
        val data = ArrayList<Any>()
        val categoryMap = hashMapOf<Int, String>()
        val annotation = hdfPath.substring(5) // returns just the annotation requested

        when {
            reader.getDataSetInformation(hdfPath).toString().contains("STRING") -> // String
                for(i in reader.string().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && asString ->
                try {
                    var entryCounter = 0

                    for (category in reader.string().readArray("/uns/" + annotation + "_categorical")) {
                        categoryMap[entryCounter] = category
                        entryCounter += 1
                    }

                    for(i in reader.int8().readArray(hdfPath)) { categoryMap[i.toInt()]?.let { data.add(it) } }

                } catch(e: HDF5SymbolTableException) { // int8 but not mapped to categorical
                    for(i in reader.int8().readArray(hdfPath)) { categoryMap[i.toInt()]?.let { data.add(it) } }
                }

            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && !asString -> // Byte
                for(i in reader.int8().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(2)") -> // Short
                    for(i in reader.int16().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(4)") -> // Int
                for(i in reader.int32().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(8)") -> // Long
                for(i in reader.int64().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(4)") -> // Float
                for(i in reader.float32().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(8)") -> // Double
                for(i in reader.float64().readArray(hdfPath)) { data.add(i) }

            reader.getDataSetInformation(hdfPath).toString().contains("BOOLEAN") -> // Boolean
                for(i in reader.int8().readArray(hdfPath)) { data.add(i) }
        }
        reader.close()
        return data
    }
}
