package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.hdf5.HDF5Factory
import hdf.hdf5lib.exceptions.HDF5SymbolTableException
import java.io.File
import java.lang.IllegalArgumentException

class AnnotationsIngest {
    private val h5adPath = "/home/luke/PycharmProjects/VRCaller/file_conversion/liver_vr_processed.h5ad"
    private val annotationsPath = "/home/luke/PycharmProjects/VRCaller/file_conversion/liver_annotations"

    private val intTypeArray = arrayOf("n_genes", "louvain", "leiden", "n_cells", "dispersions_norm")
    private val floatTypeArray = arrayOf("n_counts", "means", "dispersions")

    init{
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
        return UMAP
    }

    fun h5adAnnotationReader(pathLike: String): ArrayList<Any>{
        /**
         * path-like input of the form </obs or /var>/<annotation>
         */
        val reader = HDF5Factory.openForReading(h5adPath)
        val annType: String = pathLike.slice(1 until 4) // returns either 'var' or 'obs'
        val annotation = pathLike.substring(5) // returns just the annotation requested

        // check if inputs are valid for this dataset
        try {
            reader.getDataSetInformation(pathLike)

        } catch (e: HDF5SymbolTableException){ // Exception raised when dataset doesn't exist (H5E_SYM)
            throw IllegalArgumentException("the dataset $pathLike doesn't exist")
        }

        if((annType != "obs") && (annType != "var")) {
            throw IllegalArgumentException("annType must be either 'var' or 'obs'")
        }

        // initialize arrays
        val annotationArray = ArrayList<Any>()
        val categoryMap = hashMapOf<Int, String>()

        // try-catch to check if dataset is mapped to a categorical, extracting with corresponding mapping if so
        try {
            var entryCounter = 0

            for (category in reader.string().readArray("/uns/" + annotation + "_categorical")) {
                categoryMap[entryCounter] = category
                entryCounter += 1
            }

            for(i in reader.int8().readArray(pathLike)) {
                categoryMap[i.toInt()]?.let { annotationArray.add(it) }
            }
            println(annotationArray)
            println("try ran")

        } catch(e: HDF5SymbolTableException){
            when {
                intTypeArray.contains(annotation) -> for (i in reader.int32().readArray(pathLike)) {
                    annotationArray.add(i) }
                floatTypeArray.contains(annotation) -> for (i in reader.float32().readArray(pathLike)) {
                    annotationArray.add(i) }
                else -> for (i in reader.string().readArray(pathLike)) {
                    annotationArray.add(i) }
            }
            println(annotationArray)
            println("catching exception")
        }
        return annotationArray
    }
}
