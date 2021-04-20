package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.numerics.Random
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.*
import graphics.scenery.volumes.Colormap
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.collections.set
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import hdf.hdf5lib.exceptions.HDF5SymbolTableException

/**.
 * cellxgene interactivity - start fixing selection and marking tools
 * jar python interaction (qt applet?)
 * get imgui working
 * billboard
 *
 * serious performance hit from so many textboards? I observe around -5-7 fps
 * performance hit from toggling visibility of keys? many intersections and non-instanced nodes
 * instance key spheres? Instance textboards?
 * Textboard color off
 * hanging in vr every second or so
 * must catch and be able to encode annotations that are not categoricals! Then will work for any dataset!
 *
 * proximity with invisible sphere .Intersect then show
 * VR controller example
 * camera show information
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */

class XPlot : Node() {

    val laser = Cylinder(0.01f, 2.0f, 20)
    val laser2 = Cylinder(0.01f, 2.0f, 20)

    // define meshes that make up the scene
    var dotMesh = Mesh()
    var textBoardMesh = Mesh()
    val geneScaleMesh = Mesh()

    // a scaling factor for better scale relative to user
    private var positionScaling = 0.3f

    // global as it is required by Visualization class
    var genePicker = 0
    var annotationPicker = 0
    var annotationMode = true

    val geneBoard = TextBoard()
//    var geneNames = ArrayList<String>() // stores ordered gene names for gene board
//    var geneExpr = ArrayList<FloatArray>()

    private val annFetcher =
//        AnnotationsIngest("/home/luke/PycharmProjects/VRCaller/file_conversion/bbknn_processed.h5ad")
        AnnotationsIngest("/home/elect/IdeaProjects/XtraDimensionVR/liver_vr_processed_2.h5ad")
    private val spatialCoords = annFetcher.umapReader3D()

    var annotationList = ArrayList<String>()
    var metaOnlyAnnList = ArrayList<String>()

    // give annotations you would like (maybe with checkboxes, allow to enter the names of their annotations)
    // list of annotations
    init{
        for (ann in annFetcher.reader.getGroupMembers("/obs")){
            try {
                val info = annFetcher.reader.getDataSetInformation("/uns/" + ann + "_categorical")
                if (info.toString().toCharArray().size < 17)
                    annotationList.add(ann)
                else
                    metaOnlyAnnList.add(ann)
            } catch (e: HDF5SymbolTableException) {
                metaOnlyAnnList.add(ann)
                println("$ann is not color encodable and will exist only as metadata")
            }
        }
        println(annotationList)

    }
    //[age, batch, cell, cell_ontology_class, cell_ontology_id, free_annotation, leiden, louvain, method, mouse.id, sex, subtissue, tissue, tissue_FACS_droplet, tissue_free_annotation]

    private var annotationArray = ArrayList<FloatArray>() //used to color spheres, normalized
    private val rawAnnotations = ArrayList<ArrayList<*>>()
    private val typeList = ArrayList<String>()

    val annKeyList = ArrayList<Mesh>()
    val labelList = ArrayList<Mesh>()
    private val rgbColorSpectrum = Colormap.get("jet")

    init {
        for (ann in annotationList) {

            annotationArray.add( run {
                val raw = annFetcher.h5adAnnotationReader("/obs/$ann", false)

                rawAnnotations.add(raw.second) // used to attach metadata spheres
                typeList.add(raw.first) // grow list of annotation datatypes (used currently for metadata check for labels)
                val norm = FloatArray(raw.second.size) // array of zeros if annotation entries are all the same

                when (raw.first) {
                    "Byte" -> {
                        val max: Byte? = (raw.second as ArrayList<Byte>).maxOrNull()
                        if (max != null && max > 0f) {
                            for (i in raw.second.indices)
                                norm[i] = (raw.second[i] as Byte).toFloat() / max
                        }
                    }
                    "Short" -> {
                        val max: Short? = (raw.second as ArrayList<Short>).maxOrNull()
                        if (max != null && max > 0f) {
                            for (i in raw.second.indices)
                                norm[i] = (raw.second[i] as Short).toFloat() / max
                        }
                    }
                }
                norm
            })
            annKeyList.add(createSphereKey(ann))
        }
    }

    //generate master spheres for every 10k cells for performance
    private val masterSplit = 10_000
    private val masterCount = ceil(spatialCoords.size.toFloat() / masterSplit).toInt()
    val masterMap = hashMapOf<Int, Icosphere>()

    // initialize gene color map from scenery.Colormap
    private val encoding = "hot"
    private val colormap = Colormap.get(encoding)

    private val indexedGeneExpression = ArrayList<Float>()
    private val indexedAnnotations = ArrayList<Float>()

    var geneNames = ArrayList<String>()
    var geneExpr = ArrayList<FloatArray>()

    init {
        val (geneNameBuffer, geneExprBuffer) = annFetcher.fetchGeneExpression()
        geneNames = geneNameBuffer
        geneExpr = geneExprBuffer

        loadEnvironment()
        loadDataset()
        updateInstancingColor()
        annKeyList[0].visible = true
        labelList[0].visible = true
    }

    private fun loadDataset() {

        // hashmap to emulate at run time variable declaration
        // allows for dynamically growing number of master spheres with size of dataset
        for (i in 1..masterCount) {
            val masterTemp = Icosphere(0.04f * positionScaling, 1) // sphere properties
            masterMap[i] = addMasterProperties(masterTemp, i)
        }
        println("hashmap looks like: $masterMap")

        //create and add instances using their UMAP coordinates as position
        var resettingCounter = 0
        var parentIterator = 1

        val center = fetchCenterOfMass(spatialCoords)

        for ((counter, coord) in spatialCoords.withIndex()) {
            if (resettingCounter >= masterSplit) {
                parentIterator++
                logger.info("parentIterator: $parentIterator")
                resettingCounter = 0
            }

            val s = Mesh()

            for ((annCount, annotation) in annotationList.withIndex()) { //add all annotations as metadata (for label center of mass)
                s.metadata[annotation] = rawAnnotations[annCount][counter]
            }

            s.parent = masterMap[parentIterator]
            s.position =
                (Vector3f((coord[0] - center[0]), (coord[1] - center[1]), (coord[2] - center[2]))) * positionScaling
            s.instancedProperties["ModelMatrix"] = { s.world }
            masterMap[parentIterator]?.instances?.add(s)
            resettingCounter++
        }
        addChild(dotMesh)

        // create labels for each annotation
        for ((typeCount, annotation) in annotationList.withIndex())
            labelList.add(generateLabels(annotation, typeList[typeCount]))

    }

    fun updateInstancingColor() {
        var resettingCounter = 0
        var parentIterator = 1
        for (i in spatialCoords.indices) {
            if (resettingCounter >= masterSplit) {
                parentIterator++
                logger.info("ntparentIterator: $parentIterator")
                resettingCounter = 0
            }

            val s = masterMap[parentIterator]!!.instances[resettingCounter]
            s.dirty = true  //update on gpu

            indexedGeneExpression.clear()
            indexedAnnotations.clear()

            // index element counter of every array of gene expressions and add to new ArrayList
            for (gene in geneExpr)
                indexedGeneExpression += gene[i]

            for (annotation in annotationArray)
                indexedAnnotations += annotation[i]

            var color = if (annotationMode) {
                rgbColorSpectrum.sample(indexedAnnotations[annotationPicker])

            } else {
                colormap.sample(indexedGeneExpression[genePicker] / 10f)
            }
            // metadata "selected" stores whether point has been marked by laser. Colors marked cells red.
            if (s.metadata["selected"] == true)
                color = s.material.diffuse.xyzw()

            s.instancedProperties["Color"] = { color }

            resettingCounter++
        }
        for (master in 1..masterCount) {
            (masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        }
    }

    private fun loadEnvironment() {
        // add box to scene for sense of bound
        val boxSize = 25.0f
        val lightbox = Box(Vector3f(boxSize, boxSize, boxSize), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = Vector3f(0.4f, 0.4f, 0.4f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.Front
        addChild(lightbox)

        val lightStretch = 12.4f
        val lights = (0 until 10).map {
            val l = PointLight(radius = 50.0f)
            l.position = Vector3f(
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch)
            )
//            l.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)
            l.emissionColor = Vector3f(1f, 1f, 1f)
            l.intensity = 1f

            l
        }
        lights.forEach { addChild(it) }

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 1
        geneBoard.fontColor = Vector3f(0f, 0f, 0f).xyzw()
        geneBoard.position = Vector3f(-2.5f, 1.5f, -12.4f) // on far wall
        geneBoard.scale = Vector3f(1f, 1f, 1f)
        geneScaleMesh.addChild(geneBoard)

        // create cylinders orthogonal to each other, representing axes centered around 0,0,0 and add them to the scene
        val x = generateAxis("X", 5.00f)
        addChild(x)
        val y = generateAxis("Y", 5.00f)
        addChild(y)
        val z = generateAxis(
            "Z", 5.00f
        )
        addChild(z)

        // give lasers texture and set them to be visible (could use to have different lasers/colors/styles and switch between them)
        initializeLaser(laser)
        initializeLaser(laser2)

        val colorMapScale = Box(Vector3f(5.0f, 1.0f, 0f))
        val maxTick = TextBoard()
        val minTick = TextBoard()

        colorMapScale.material.textures["diffuse"] =
            Texture.fromImage(Image.fromResource("volumes/colormap-$encoding.png", this::class.java))
        colorMapScale.material.metallic = 0.3f
        colorMapScale.material.roughness = 0.9f
        colorMapScale.position = Vector3f(0f, 3f, -12.4f)
        geneScaleMesh.addChild(colorMapScale)

        minTick.text = "0"
        minTick.transparent = 1
        minTick.fontColor = Vector3f(0.03f, 0.03f, 0.03f).xyzw()
        minTick.position = Vector3f(-2.5f, 3.5f, -12.4f)
        geneScaleMesh.addChild(minTick)

        maxTick.text = "10"
        maxTick.transparent = 1
        maxTick.fontColor = Vector3f(0.03f, 0.03f, 0.03f).xyzw()
        maxTick.position = Vector3f(2.1f, 3.5f, -boxSize / 2 + 0.1f)
        geneScaleMesh.addChild(maxTick)

        geneScaleMesh.visible = false
        addChild(geneScaleMesh)
    }

    private fun generateLabels(annotation: String, type: String): Mesh {
        println("label being made")
        val m = Mesh()
        val mapping = annFetcher.h5adAnnotationReader("/uns/" + annotation + "_categorical") as Pair<String, ArrayList<String>> // don't use .first

        val mapSize = if (mapping.second.size > 1) mapping.second.size - 1 else 1
        for ((count, label) in mapping.second.withIndex()) {
            val t = TextBoard()
            t.text = label.replace("ï", "i")
            t.transparent = 0
            t.fontColor = Vector3f(0f, 0f, 0f).xyzw()
            t.backgroundColor = rgbColorSpectrum.sample(count.toFloat() / mapSize)
            when (type) {
                "Byte" -> t.position = fetchCellLabelPosition(annotation, count.toByte())
                "Short" -> t.position = fetchCellLabelPosition(annotation, count.toShort())
            }
            t.scale = Vector3f(0.2f, 0.2f, 0.2f) * positionScaling
            m.addChild(t)
        }

        m.visible = false
        addChild(m)
        return m
    }

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCellLabelPosition(annotation: String, type: Any): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for (master in 1..masterCount) {
            for (instance in masterMap[master]!!.instances.filter { it.metadata[annotation] == type }) {
                additiveMass[0] += instance.position.toFloatArray()[0]
                additiveMass[1] += instance.position.toFloatArray()[1]
                additiveMass[2] += instance.position.toFloatArray()[2]

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
        val mapping = annFetcher.h5adAnnotationReader("/uns/" + annotation + "_categorical")
        // first: type, second: array

        val rootPosY = 10f
        val rootPosX = -10.5f
        val scale = 0.4f

        val sizeList = arrayListOf<Int>()
        val overflowLim = (22 / scale).toInt()
        var overflow = 0
        var maxString = 0

        for (cat in mapping.second) {
            if (overflow < overflowLim) {
                val len = cat.toString().toCharArray().size
                if (len > maxString)
                    maxString = len
                overflow++
            } else {
                if (maxString < scale * 70)
                    sizeList.add(maxString)
                else
                    sizeList.add((scale * 70).toInt())
                maxString = 0
                overflow = 0
            }
        }
        val title = TextBoard()
        title.transparent = 1
        title.text = annotation
        title.scale = Vector3f(scale * 2)
        title.fontColor = Vector3f(0f, 0f, 0f).xyzw()
        title.position = Vector3f(rootPosX + scale, rootPosY, -11f)
        m.addChild(title)

        overflow = 0
        var lenIndex = -1 // -1 so first column isn't shifted
        var charSum = 0
        val mapSize = if (mapping.second.size > 1) mapping.second.size - 1 else 1

        for ((colorIncrement, cat) in mapping.second.withIndex()) {
            val key = TextBoard()
            val tooLargeBy = cat.toString().toCharArray().size - (scale * 70)
            when {
                (tooLargeBy >= 0) ->
                    key.text = cat.toString().replace("ï", "i").dropLast(tooLargeBy.toInt() + 5) + "..."// not utf-8 -_-
                (tooLargeBy < 0) ->
                    key.text = cat.toString().replace("ï", "i") // not utf-8 -_-
            }

            key.fontColor = Vector3f(0f, 0f, 0f).xyzw()
            key.transparent = 1
            key.scale = Vector3f(scale)

            val sphere = Icosphere(scale / 2, 3)
            sphere.material.diffuse = rgbColorSpectrum.sample(colorIncrement.toFloat() / mapSize).xyz()
            sphere.material.ambient = Vector3f(0.3f, 0.3f, 0.3f)
            sphere.material.specular = Vector3f(0.1f, 0.1f, 0.1f)
            sphere.material.roughness = 0.19f
            sphere.material.metallic = 0.0001f

            if (lenIndex == -1) {
                key.position = Vector3f(rootPosX + scale, rootPosY - (overflow + 1) * scale, -11f)
                sphere.position = Vector3f(rootPosX, (rootPosY - (overflow + 1) * scale) + scale / 2, -11f)
            } else {
                key.position =
                    Vector3f(rootPosX + scale + (charSum * 0.31f * scale), rootPosY - (overflow + 1) * scale, -11f)
                sphere.position = Vector3f(
                    rootPosX + (charSum * 0.31f * scale),
                    (rootPosY - (overflow + 1) * scale) + scale / 2,
                    -11f
                )
            }
            m.addChild(sphere)
            m.addChild(key)
            overflow++

            if (overflow == overflowLim && sizeList.size > 0) { // also checking for case of mapping.size == overFlowLim
                lenIndex++
                overflow = 0
                charSum += if (sizeList[lenIndex] < 5) 18
                else sizeList[lenIndex]
                
            }
        }
        m.visible = false
        addChild(m)
        return m
    }

    private fun addMasterProperties(master: Icosphere, masterNumber: Int): Icosphere {
        /*
       Generate an Icosphere used as Master for 10k instances.
       Override shader to allow for varied color on instancing and makes color an instanced property.
        */
        master.metadata["MaxInstanceUpdateCount"] = AtomicInteger(1)
//        (metadata["MaxInstanceUpdateCount"] as? AtomicInteger)?.getAndIncrement()
        master.name = "master$masterNumber"
        master.material = ShaderMaterial(
            Shaders.ShadersFromFiles(
                arrayOf(
                    "DefaultDeferredInstanced.frag",
                    "DefaultDeferredInstancedColor.vert"
                ), XPlot::class.java
            )
        ) //overrides the shader
        master.material.ambient = Vector3f(0.3f, 0.3f, 0.3f)
        master.material.specular = Vector3f(0.1f, 0.1f, 0.1f)
        master.material.roughness = 0.19f
        master.material.metallic = 0.0001f
        master.instancedProperties["ModelMatrix"] = { master.world }
        master.instancedProperties["Color"] = { master.material.diffuse.xyzw() }

        dotMesh.addChild(master)

        return master
    }

    private fun initializeLaser(laserName: Cylinder) {
        laserName.material.diffuse = Vector3f(0.9f, 0.0f, 0.0f)
        laserName.material.metallic = 0.001f
        laserName.material.roughness = 0.18f
        laserName.rotation.rotateX(-Math.PI.toFloat() / 1.5f) // point laser forwards
        laserName.visible = true
    }

    private fun generateAxis(dimension: String = "x", length: Float = 5.00f): Cylinder {
        val cyl: Cylinder = when (dimension.capitalize()) {
            "X" -> {
                Cylinder.betweenPoints(Vector3f(-length, 0f, 0f), Vector3f(length, 0f, 0f), radius = 0.01f)
            }
            "Y" -> {
                Cylinder.betweenPoints(Vector3f(0f, -length, 0f), Vector3f(0f, length, 0f), radius = 0.01f)
            }
            "Z" -> {
                Cylinder.betweenPoints(Vector3f(0f, 0f, -length), Vector3f(0f, 0f, length), radius = 0.01f)
            }
            else -> throw IllegalArgumentException("$dimension is not a valid dimension")
        }
        cyl.material.roughness = 0.18f
        cyl.material.metallic = 0.001f
        cyl.material.diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        return cyl
    }

    fun resetVisibility() {
        for (i in 0..masterMap.size) {
            masterMap[i]?.instances?.forEach {
                it.visible = true
                it.metadata.remove("selected")
            }
        }
    }

    fun loadNewGenes() {
        thread {
            Thread.currentThread().priority = Thread.MIN_PRIORITY

            geneBoard.text = "fetching..."

            val (geneNameBuffer, geneExprBuffer) = annFetcher.fetchGeneExpression()
            genePicker = 0
            geneNames.clear()
            geneExpr.clear()
            geneNames = geneNameBuffer
            geneExpr = geneExprBuffer

            updateInstancingColor()
            geneBoard.text = "Gene: " + geneNames[genePicker]
        }
    }
}
