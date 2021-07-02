package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.base.mdarray.MDFloatArray
import ch.systemsx.cisd.base.mdarray.MDIntArray
import ch.systemsx.cisd.hdf5.HDF5Factory
import ch.systemsx.cisd.hdf5.IHDF5Reader
import graphics.scenery.numerics.Random
import hdf.hdf5lib.exceptions.HDF5SymbolTableException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil


class AnnotationsIngest(h5adPath: String) {
    val reader: IHDF5Reader = HDF5Factory.openForReading(h5adPath)
    private val geneNames = h5adAnnotationReader("/var/index")

    private val cscData: MDFloatArray = reader.float32().readMDArray("/X/data")
    private val cscIndices: MDIntArray = reader.int32().readMDArray("/X/indices")
    private val cscIndptr: MDIntArray = reader.int32().readMDArray("/X/indptr")

    val numGenes = reader.string().readArrayRaw("/var/index").size
    val numCells = reader.string().readArrayRaw("/obs/index").size

    val nonZeroGenes = ArrayList<Int>()

    init {
        val ratioList = ArrayList<Float>()
        for (geneIndex in 0 until numGenes) {
            ratioList.add((cscIndptr[geneIndex + 1] - cscIndptr[geneIndex]).toFloat() / numCells.toFloat())

            if (ratioList[geneIndex] > 0.2) {
                nonZeroGenes.add(geneIndex)
            }
        }
        println(nonZeroGenes.size)
        println(numGenes)
    }

    fun fetchGeneExpression(genes: ArrayList<Int> = arrayListOf()): Triple<ArrayList<String>, ArrayList<FloatArray>, ArrayList<Int>> {
        if (genes.isEmpty()) {
            for (i in 0..20) {
                genes.add(Random.randomFromRange(0f, numGenes.toFloat()).toInt())
            }
        }
        val expressions = (genes.map { cscReader(it) } as ArrayList<FloatArray>)

        // normalize between 0 and 10
        val maxList = ArrayList<Float>()  // save in list for access by color map labels
        for (gene in 0 until genes.size) {
            val max = ceil(expressions[gene].maxOrNull()!!)
                when {
                    max == 0f -> maxList.add(10f)
                    max > 0f -> {
                        maxList.add(max)
                        for (expr in 0 until numCells) {
                            expressions[gene][expr] *= (10 / maxList[gene])
                        }
                    }
                }
        }

        val names = genes.map { geneNames[it] } as ArrayList<String>
        println(names)

        return Triple(names, expressions, maxList.map {it.toInt()} as ArrayList<Int>)
    }

    fun umapReader3D(): ArrayList<ArrayList<Float>> {
        val umap = arrayListOf<ArrayList<Float>>()

        var tripletCounter = 0
        val cellUMAP = ArrayList<Float>()

        for (coordinate in reader.float32().readArray("/obsm/X_umap")) {
            if (tripletCounter < 3) {
                cellUMAP.add(coordinate)
                tripletCounter += 1
            } else {
                umap.add(
                    arrayListOf(
                        cellUMAP[0],
                        cellUMAP[1],
                        cellUMAP[2]
                    )
                ) // actual values instead of pointer to object
                cellUMAP.clear()
                tripletCounter = 1 // zero for first loop, then 1, as first entry is added in else clause
                cellUMAP.add(coordinate)
            }
        }
        umap.add(arrayListOf(cellUMAP[0], cellUMAP[1], cellUMAP[2])) // add final sub-array

        return umap
    }

    fun h5adAnnotationReader(hdfPath: String, asString: Boolean = true): ArrayList<*> {
        /**
         * reads any 1 dimensional annotation (ie obs, var, uns from scanPy output), checking if a categorical map exists for them
         **/
        if (hdfPath[4].toString() != "/")
            throw InputMismatchException("this function is only for reading obs, var, and uns")

        val data = ArrayList<Any>()
        val categoryMap = hashMapOf<Int, String>()
        val annotation = hdfPath.substring(5) // returns just the annotation requested

        when {
            reader.getDataSetInformation(hdfPath).toString().contains("STRING") -> {// String
                for (i in reader.string().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && asString ->
                try {
                    for ((counter, category) in reader.string().readArray("/uns/" + annotation + "_categorical")
                        .withIndex())
                        categoryMap[counter] = category

                    for (i in reader.int8().readArray(hdfPath))
                        categoryMap[i.toInt()]?.let { data.add(it) }

                } catch (e: HDF5SymbolTableException) { // int8 but not mapped to categorical
                    for (i in reader.int8().readArray(hdfPath))
                        categoryMap[i.toInt()]?.let { data.add(it) }
                }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && !asString -> {// Byte
                for (i in reader.int8().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(2)") -> {// Short
                for (i in reader.int16().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(4)") -> {// Int
                for (i in reader.int32().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(8)") -> {// Long
                for (i in reader.int64().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(4)") -> {// Float
                for (i in reader.float32().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(8)") -> {// Double
                for (i in reader.float64().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("BOOLEAN") -> {// Boolean
                for (i in reader.int8().readArray(hdfPath))
                    data.add(i)
            }
        }
        return data
    }

    /**
     * return dense column of gene expression values for a chosen column / gene
     */
    fun cscReader(col: GeneIndex = 0): FloatArray {
        val exprArray = FloatArray(numCells)

        val start = cscIndptr[col]
        val end = cscIndptr[col + 1]

        for (i in start until end) {
            exprArray[cscIndices[i]] = cscData[i]
        }
        return exprArray
    }
}
