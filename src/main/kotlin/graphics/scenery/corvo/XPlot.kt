package graphics.scenery.corvo

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.extensions.*
import graphics.scenery.volumes.Colormap
import org.apache.commons.math3.stat.inference.MannWhitneyUTest
import org.apache.commons.math3.stat.inference.TTest
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.set
import kotlin.math.*

/**.
 * billboard textboards
 * simplify controls
 * performance hit from so many textboards? I observe around -5-7 fps
 * performance hit from toggling visibility of keys? many intersections and non-instanced nodes
 * instance key spheres? Instance textboards?
 * VR controller example
 *
 * @author Luke Hyman <lukehcode@gmail.com>
 */

var geneNames = ArrayList<String>()
var geneExpr = ArrayList<FloatArray>()
var maxExprList = ArrayList<Int>()
class XPlot(filePath: String) : RichNode() {

    // a scaling factor for better scale relative to user
    private var positionScaling = 0.3f
    private val colormap = Colormap.get(encoding)

    val annFetcher = AnnotationsIngest(filePath)
    private val spatialCoords = annFetcher.umapReader3D()

    // give annotations you would like (maybe with checkboxes, allow to enter the names of their annotations)
    // list of annotations

    private var annotationArray = ArrayList<FloatArray>() //used to color spheres, normalized
    private val codedAnnotations = ArrayList<ArrayList<*>>()
    private val typeList =
        ArrayList<String>() //grow list of annotation datatypes (used currently for metadata check for labels)

    val annKeyList = ArrayList<Mesh>()
    val labelList = ArrayList<Mesh>()
    val rgbColorSpectrum = Colormap.get("jet")

    val container = RichNode("cell container")

    init {
        for (ann in annotationList) {
            logger.info("annotation: $ann")

            annotationArray.add(run {
                val codes = annFetcher.h5adAnnotationReader("/obs/$ann/codes")
                codedAnnotations.add(codes) // used to attach metadata spheres
                val norm = FloatArray(codes.size) // array of zeros if annotation entries are all the same

                when (codes[0]) {
                    is Byte -> {
                        typeList.add("Byte")
                        val max: Byte? = (codes as ArrayList<Byte>).maxOrNull()
                        if (max != null && max > 0f) {
                            for (i in codes.indices)
                                norm[i] = (codes[i]).toFloat() / max
                        }
                    }
                    is Short -> {
                        typeList.add("Short")
                        val max: Short? = (codes as ArrayList<Short>).maxOrNull()
                        if (max != null && max > 0f) {
                            for (i in codes.indices)
                                norm[i] = (codes[i]).toFloat() / max
                        }
                    }
                }
                norm
            })
            annKeyList.add(createSphereKey(ann))
        }
    }

    //generate master spheres for every 10k cells (rendering and parsing performance improvement)
    private val masterSplit = 10_000
    private val masterCount = ceil(spatialCoords.size.toFloat() / masterSplit).toInt()
    val instancedNodeMap = hashMapOf<Int, InstancedNode>()
    init {
        val buffer = annFetcher.fetchGeneExpression()
        geneNames = buffer.first
        geneExpr = buffer.second
        maxExprList = buffer.third  // max of each gene from normalization - used by color map label

        loadDataset()
        updateInstancingArrays()
        updateInstancingLambdas()
        annKeyList[annotationPicker].visible = true
    }

    private fun loadDataset() {
        val sphereSize = 0.03f

        // hashmap to emulate at run time variable declaration, for dynamically growing n
        // allows for dynamically growing number of master spheres with size of dataset
        println(masterCount)
        for (i in 1..masterCount) {
            val masterTemp = Icosphere(sphereSize * positionScaling, 1) // sphere properties
            instancedNodeMap[i] = addMasterProperties(masterTemp, i)
        }
        addChild(dotMesh)
        logger.info("hashmap looks like: $instancedNodeMap")

        //create and add instances using their UMAP coordinates as position
        val center = fetchCenterOfMass(spatialCoords)

        var resettingCounter = 0
        var parentIterator = 1

        for ((counter, coord) in spatialCoords.withIndex()) {
            if (resettingCounter >= masterSplit) {
                parentIterator++
                logger.info("parentIterator: $parentIterator")
                resettingCounter = 0
            }
            val s = instancedNodeMap[parentIterator]!!.addInstance()
            s.addChild(Sphere(0.01f, 1))

            for ((annCount, annotation) in annotationList.withIndex()) {  //add all annotations as metadata (for label center of mass)
                s.metadata[annotation] = codedAnnotations[annCount][counter]
            }

            s.metadata["index"] = counter  // used to identify row of the cell
            s.parent = container
            s.spatial().position =
                (Vector3f(
                    (coord[0] - center[0]),
                    (coord[1] - center[1] + 10f),
                    (coord[2] - center[2])
                )) * positionScaling

            resettingCounter++
        }
        addChild(container)

//      create labels for each annotation
        for ((typeCount, annotation) in annotationList.withIndex()) {
            labelList.add(generateLabels(annotation, typeList[typeCount]))
        }
        addChild(textBoardMesh)
    }

    /**
     * updateInstancingArrays creates new data arrays for each instance
     * only needs to be called at launch or when new data such as gene expression needs to be loaded
     */
    fun updateInstancingArrays() {
        var resettingCounter = 0
        var parentIterator = 1
        for (i in spatialCoords.indices) {
            if (resettingCounter >= masterSplit) {
                parentIterator++
                resettingCounter = 0
            }
            val indexedGeneExpression = ArrayList<Float>()
            val indexedAnnotations = ArrayList<Float>()

            // index element counter of every array of gene expressions and add to new ArrayList
            for (gene in geneExpr)
                indexedGeneExpression += gene[i]

            for (annotation in annotationArray)
                indexedAnnotations += annotation[i]

            val s = instancedNodeMap[parentIterator]!!.instances[resettingCounter]
            s.metadata["colors"] = arrayOf(  // fix current state as metadata to avoid race conditions
                indexedGeneExpression,
                indexedAnnotations
            )

            resettingCounter++
        }
        for (master in 1..masterCount) {
            (instancedNodeMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        }
    }

    /**
     * updates the color instancing property of each point with the current index of the data arrays
     */
    fun updateInstancingLambdas() {

        var resettingCounter = 0
        var parentIterator = 1

        for (i in spatialCoords.indices) {

            if (resettingCounter >= masterSplit) {
                parentIterator++
                resettingCounter = 0
            }
            val s = instancedNodeMap[parentIterator]!!.instances[resettingCounter]
            s.instancedProperties["Color"] = { Vector4f(1f, 0f, 0f, 0f) }
            s.instancedProperties["Color"] = {
                when {
                    s.metadata["selected"] == true -> Vector4f(1.0f) //experimental replacement

                    annotationMode -> rgbColorSpectrum.sample((s.metadata["colors"] as Array<ArrayList<Float>>)[1][annotationPicker] * 0.99f)

                    !annotationMode -> colormap.sample((s.metadata["colors"] as Array<ArrayList<Float>>)[0][genePicker] / 10.1f)

                    else -> Vector4f(1f, 0f, 0f, 1f)
                }
            }
            resettingCounter++
        }
        for (master in 1..masterCount) {
            (instancedNodeMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        }
    }

    private fun generateLabels(annotation: String, type: String): Mesh {
        logger.info("label being made")

        val m = Mesh()
        val mapping = annFetcher.h5adAnnotationReader("/obs/$annotation/categories") as ArrayList<String>

        val mapSize = if (mapping.size > 1) mapping.size - 1 else 1

        for ((count, label) in mapping.withIndex()) {
            val t = TextBoard("SourceSansPro-Light.ttf")

            t.text = label.replace("ï", "i")
            t.transparent = 0
            t.fontColor = Vector3f(0f, 0f, 0f).xyzw()
            t.backgroundColor = rgbColorSpectrum.sample((count.toFloat() / mapSize) * 0.99f)

            when (type) {
                "Byte" -> t.spatial().position = fetchCellLabelPosition(annotation, count.toByte())
                "Short" -> t.spatial().position = fetchCellLabelPosition(annotation, count.toShort())
            }

            t.spatial().scale = Vector3f(0.3f, 0.3f, 0.3f) * positionScaling

            t.addChild(Sphere(0.1f, 1))
            m.addChild(t)
        }

        m.visible = false
        textBoardMesh.addChild(m)
        return m
    }

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCellLabelPosition(annotation: String, type: Any): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for (master in 1..masterCount) {
            for (instance in instancedNodeMap[master]!!.instances.filter { it.metadata[annotation] == type }) {
                additiveMass[0] += instance.spatial().position.toFloatArray()[0]
                additiveMass[1] += instance.spatial().position.toFloatArray()[1]
                additiveMass[2] += instance.spatial().position.toFloatArray()[2]

                filteredLength++
            }
        }
        return Vector3f(
            (additiveMass[0] / filteredLength),
            (additiveMass[1] / filteredLength),
            (additiveMass[2] / filteredLength)
        )
    }

    private fun fetchCenterOfMass(coordArray: ArrayList<ArrayList<Float>>): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0

        for (entry in coordArray) {
            for (axis in 0..2) {
                additiveMass[axis] += entry[axis]
            }
            filteredLength++
        }
        return Vector3f(
            (additiveMass[0] / filteredLength),
            (additiveMass[1] / filteredLength),
            (additiveMass[2] / filteredLength)
        )
    }

    private fun createSphereKey(annotation: String): Mesh {
        val m = Mesh()
        val mapping = annFetcher.h5adAnnotationReader("/obs/$annotation/categories")

        val rootPosY = 15f
        val rootPosX = -8.5f
        val keyScale = 0.6f

        val sizeList = arrayListOf<Int>()
        val overflowLim = (22 / keyScale).toInt()
        var overflow = 0
        var maxString = 0

        for (cat in mapping) {
            if (overflow < overflowLim) {
                val len = cat.toString().toCharArray().size
                if (len > maxString)
                    maxString = len
                overflow++
            } else {
                if (maxString < keyScale * 70)
                    sizeList.add(maxString)
                else
                    sizeList.add((keyScale * 70).toInt())
                maxString = 0
                overflow = 0
            }
        }

        val title = TextBoard("SourceSansPro-Light.ttf")
        title.transparent = 1
        title.text = annotation
        title.fontColor = Vector3f(180 / 255f, 23 / 255f, 52 / 255f).xyzw()
        title.spatial {
            scale = Vector3f(keyScale * 2)
            position = Vector3f(rootPosX + keyScale, rootPosY, -11f)
        }

        m.addChild(title)

        overflow = 0
        var lenIndex = -1 // -1 so first column isn't shifted
        var charSum = 0
        val mapSize = if (mapping.size > 1) mapping.size - 1 else 1

        for ((colorIncrement, cat) in mapping.withIndex()) {

            val key = TextBoard("SourceSansPro-Regular.ttf")
            val tooLargeBy = cat.toString().toCharArray().size - (keyScale * 70)

            when {
                (tooLargeBy >= 0) ->
                    key.text = cat.toString().replace("ï", "i").dropLast(tooLargeBy.toInt() + 5) + "..."// not utf-8 -_-
                (tooLargeBy < 0) ->
                    key.text = cat.toString().replace("ï", "i") // not utf-8 -_-
            }

            key.fontColor = Vector3f(1f, 1f, 1f).xyzw()
            key.transparent = 1
            key.spatial().scale = Vector3f(keyScale)


            val sphere = Icosphere(keyScale / 2, 2)
            sphere.material {
                ambient = Vector3f(0.3f, 0.3f, 0.3f)
                specular = Vector3f(0.1f, 0.1f, 0.1f)
                roughness = 0.19f
                metallic = 0.0001f
                diffuse = rgbColorSpectrum.sample((colorIncrement.toFloat() / mapSize) * 0.99f).xyz()
            }

            if (lenIndex == -1) {
                key.spatial().position = Vector3f(rootPosX + keyScale, rootPosY - (overflow + 1) * keyScale, -11f)
                sphere.spatial().position = Vector3f(rootPosX, (rootPosY - (overflow + 1) * keyScale) + keyScale / 2, -11f)

            } else {
                key.spatial().position =
                    Vector3f(rootPosX + keyScale + (charSum * 0.31f * keyScale), rootPosY - (overflow + 1) * keyScale, -11f)
                sphere.spatial().position = Vector3f(
                    rootPosX + (charSum * 0.31f * keyScale),
                    (rootPosY - (overflow + 1) * keyScale) + keyScale / 2,
                    -11f
                )
            }

            m.addChild(sphere)
            m.addChild(key)
            overflow++

            if (overflow == overflowLim && sizeList.size > 0) { // also checking for case of mapping.size == overFlowLim
                lenIndex++
                overflow = 0
                charSum += if (sizeList[lenIndex] < 12) 18
                else sizeList[lenIndex]

            }
        }
//        m.addChild(parent)
        m.visible = false
        addChild(m)
        return m
    }

    /**
     Create master icospere and corresponding instancedNode for every 10k instances.
     Override shader to allow for varied color on instancing and makes color an instanced property.
     */
    private fun addMasterProperties(master: Icosphere, masterNumber: Int): InstancedNode {
        master.name = "master$masterNumber"
        master.setMaterial(ShaderMaterial(
            Shaders.ShadersFromFiles(
                arrayOf(
                    "DefaultDeferredInstanced.frag",
                    "DefaultDeferredInstancedColor.vert"
                ), XPlot::class.java
            )))

        val instancedMaster = InstancedNode(master)

        instancedMaster.instancedProperties["Color"] = { master.material().diffuse.xyzw() }
        instancedMaster.metadata["MaxInstanceUpdateCount"] = AtomicInteger(1)

        dotMesh.addChild(instancedMaster)
        return instancedMaster
    }

    fun maxDiffExpressedGenes(
        selectedCells: ArrayList<Int>,
        backgroundCells: ArrayList<Int>,
        method: String = "Mann"
    ): ArrayList<Int> {
        // looking for biggest t, ie the most significant difference in the two distributions
        val pMap = HashMap<Int, Double>()
        val maxGenesList = ArrayList<Int>()

        for (geneIndex in annFetcher.nonZeroGenes) {

            val expression = annFetcher.cscReader(geneIndex)

            val selectedArray = ArrayList<Double>()
            val backgroundArray = ArrayList<Double>()

            selectedCells.forEach { selectedArray.add(expression[it].toDouble()) }

            backgroundCells.forEach { backgroundArray.add(expression[it].toDouble()) }

            pMap[geneIndex] = if (method == "TTest") TTest().tTest(
                selectedArray.toDoubleArray(),
                backgroundArray.toDoubleArray()
            )
            else MannWhitneyUTest().mannWhitneyUTest(
                selectedArray.toDoubleArray(),
                backgroundArray.toDoubleArray()
            )
        }
        for (i in 0..9) {
            val maxKey = pMap.minByOrNull { it.value }?.key
            if (maxKey != null) {
                maxGenesList.add(maxKey)
            }
            pMap.remove(maxKey)
        }
        return maxGenesList
    }

}
