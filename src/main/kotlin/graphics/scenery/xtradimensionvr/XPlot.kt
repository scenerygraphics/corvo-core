package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.extensions.xyzw
import graphics.scenery.volumes.Colormap
import org.apache.commons.io.filefilter.TrueFileFilter
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.concurrent.thread
import kotlin.math.ceil


/**.
 *
 * cellxgene interactivity - start building under the hood tools needed
 * jar python interaction (qt applet?)
 * selection and read of new genes from thread
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */

class XPlot : Node() {

    val laser = Cylinder(0.01f, 2.0f, 20)
    val laser2 = Cylinder(0.01f, 2.0f, 20)

    // define meshes that make up the scene
    var dotMesh = Mesh()
    var textBoardMesh = Mesh()

    // a scaling factor for better scale relative to user
    var positionScaling = 0.2f

    // global as it is required by Visualization class
    var globalMasterMap = hashMapOf<Int, Icosphere>()
    var genePicker = 0
    var textBoardPicker = true
    val geneBoard = TextBoard()
    lateinit var cameraLight: PointLight
    val geneNames = ArrayList<String>()

    val annotationFetcher = AnnotationsIngest()
//        val (spatialCoords, cellNames, geneIndexList) = annotationFetcher.fetchTriple(geneNames)

    var geneExpr = annotationFetcher.fetchGeneExpression(geneNames)
    val spatialCoords = annotationFetcher.UMAPReader3D()
    val cellNames = annotationFetcher.h5adAnnotationReader("/obs/cell_ontology_class") as ArrayList<String>

    //generate master spheres for every 10k cells for performance
    val masterCount = ceil(cellNames.size.toFloat() / 10000).toInt()

    init {
        if (masterCount == 0) {
            throw java.lang.IndexOutOfBoundsException("no cells could be found")
        }
    }

    val tabulaColorMap = HashMap<String, Vector3f>()

    val uniqueCellNames = cellNames.toSet()

    init {
        // generate random color vector for each cell ontology
        for (i in uniqueCellNames) {
            tabulaColorMap[i] = Random.random3DVectorFromRange(0.2f, 1.0f)
        }
    }

    // initialize gene color map from scenery.Colormap
    val colormap = Colormap.get("hot")

    val indexedGeneExpression = ArrayList<Float>()

    val masterMap = hashMapOf<Int, Icosphere>()

    init {
        loadEnvironment()
        loadDataset()
        updateInstancingColor()
    }

    private fun loadDataset() {
        /*
        Instancing
        - Create parent sphere that instances inherit from.
        - Instanced properties are position and color.
        - All instances and masters exist in dotMesh.
         */

        // hashmap to emulate at run time variable declaration
        // allows for dynamically growing number of master spheres with size of dataset
        for (i in 1..masterCount) {
            val masterTemp = Icosphere(0.05f * positionScaling, 3)
            masterMap[i] = addMasterProperties(masterTemp, i)
        }
        println("hashmap looks like: $masterMap")
        // give access to hashmap of master objects to functions outside of init and to XVisualization class
        globalMasterMap = masterMap

        /*
        create and add instances using their UMAP coordinates as position and cell ontology or gene expression as color
         */
        var zipCounter = 0
        var resettingZipCounter = 0
        var parentIterator = 1

        cellNames.zip(spatialCoords) { cell, coord ->
            if (resettingZipCounter >= 10000) {
                parentIterator += 1
                logger.info("parentIterator: $parentIterator")
                resettingZipCounter = 0
            }

            val s = Mesh()

            // index element zipCounter of every array of gene expressions and add to new ArrayList
            for (gene in geneExpr) {
                indexedGeneExpression.add(gene[zipCounter])
            }

            s.parent = masterMap[parentIterator]
            s.name = cell as String
            s.position = Vector3f(coord[0], coord[1], coord[2]) * positionScaling

            s.instancedProperties["ModelMatrix"] = { s.world }

            masterMap[parentIterator]?.instances?.add(s)
            zipCounter += 1
            resettingZipCounter += 1
        }

        // fetch center of mass for each cell type and attach TextBoard with cell type at that location
        val massMap = textBoardPositions(uniqueCellNames)
        for (i in uniqueCellNames) {
            val t = TextBoard(isBillboard = true)
            t.text = i
            t.name = i
            t.transparent = 0
            t.fontColor = Vector3f(0.05f, 0.05f, 0.05f).xyzw()
            t.backgroundColor = tabulaColorMap[i]!!.xyzw()
            t.position = massMap[i]!!
            t.scale = Vector3f(0.3f, 0.3f, 0.3f) * positionScaling
            textBoardMesh.addChild(t)
        }

        // add all data points and their labels (if color encodes cell type) to the scene
        addChild(textBoardMesh)
        addChild(dotMesh)
    }

    fun updateInstancingColor() {
        var zipCounter = 0
        var resettingZipCounter = 0
        var parentIterator = 1
        for (cell in cellNames) {
            if (resettingZipCounter >= 10_000) {
                parentIterator += 1
                logger.info("parentIterator: $parentIterator")
                resettingZipCounter = 0
            }

            val s = masterMap[parentIterator]!!.instances[resettingZipCounter]
            s.dirty = true  //update on gpu

            indexedGeneExpression.clear()
            // index element zipCounter of every array of gene expressions and add to new ArrayList
            for (gene in geneExpr) {
                indexedGeneExpression.add(gene[zipCounter])

            }
//            println(genePicker)
//            println(indexedGeneExpression[1] / 10f)
//            println(colormap.sample(indexedGeneExpression[1] / 10f))
            s.instancedProperties["Color"] = {
                var color = if (textBoardPicker) {
                    // cell type encoded as color
                    tabulaColorMap.getOrDefault(cell, Vector3f(1.0f, 0f, 0f)).xyzw()

                } else {
                    colormap.sample(indexedGeneExpression[genePicker] / 10f)
                }

                // metadata "selected" stores whether point has been marked by laser. Colors marked cells red.
                (s.metadata["selected"] as? Boolean)?.let {
                    if (it) {
                        color = s.material.diffuse.xyzw()
                    }
                }
                color
            }
            zipCounter += 1
            resettingZipCounter += 1
        }
    }

    private fun loadEnvironment() {
        // add box to scene for sense of bound
        val lightbox = Box(Vector3f(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = Vector3f(0.4f, 0.4f, 0.4f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.None
        addChild(lightbox)

        val lightStretch = 12f
        val lights = (0 until 8).map {
            val l = PointLight(radius = 95.0f)
            l.position = Vector3f(
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch)
            )
//            l.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)
            l.emissionColor = Vector3f(0.4f, 0.4f, 0.4f)
            l.intensity = Random.randomFromRange(0.7f, 1.0f)

            l
        }

        // light attached to the camera
        cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        cameraLight.intensity = 0.8f

        lights.forEach { lightbox.addChild(it) }

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 1
        geneBoard.fontColor = Vector3f(0f, 0f, 0f).xyzw()
        geneBoard.position = Vector3f(-5f, 0f, -9f) // on far wall
        geneBoard.visible = false
//        geneBoard.position = Vector3f(0f, 0f, -0.2f)
//        geneBoard.scale = Vector3f(0.05f, 0.05f, 0.05f)
        geneBoard.scale = Vector3f(1f, 1f, 1f)

        addChild(geneBoard)

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
    }

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCenterOfMass(type: String): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for (i in globalMasterMap[1]!!.instances.filter { it.name == type }) {
            additiveMass[0] += i.position.toFloatArray()[0]
            additiveMass[1] += i.position.toFloatArray()[1]
            additiveMass[2] += i.position.toFloatArray()[2]

            filteredLength += 1
        }
        return Vector3f(
            (additiveMass[0] / filteredLength),
            (additiveMass[1] / filteredLength),
            (additiveMass[2] / filteredLength)
        )
    }

    // for each unique cell type in the dataset, calculate the average position of all of its instances
    private fun textBoardPositions(cellNameSet: Set<String>): HashMap<String, Vector3f> {
        val massMap = HashMap<String, Vector3f>()
        for (i in cellNameSet) {
            massMap[i] = fetchCenterOfMass(i)
            logger.info("center of mass for $i is: ${fetchCenterOfMass(i)}")
        }
        return massMap
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

    private fun addMasterProperties(master: Icosphere, masterNumber: Int): Icosphere {
        /*
       Generate an Icosphere used as Master for 10k instances.
       Override shader to allow for varied color on instancing and makes color an instanced property.
        */

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
        master.material.roughness = 0.18f
        master.material.metallic = 0.001f
        master.instancedProperties["ModelMatrix"] = { master.world }
        master.instancedProperties["Color"] = { master.material.diffuse.xyzw() }

        dotMesh.addChild(master)

        return master
    }

    fun resetVisibility() {
        for (i in 0..globalMasterMap.size) {
            globalMasterMap[i]?.instances?.forEach {
                it.visible = true
                it.metadata.remove("selected")
            }
        }
    }

    fun reload() {
        thread {
            geneNames.clear()
            geneBoard.text = " "
            genePicker = 0
            val annotationFetcher = AnnotationsIngest()
            geneExpr = annotationFetcher.fetchGeneExpression(geneNames)
//            val spatialCoords = annotationFetcher.UMAPReader3D()
//            val cellNames = annotationFetcher.h5adAnnotationReader("/obs/cell_ontology_class")


//            removeChild(dotMesh)
//            removeChild(textBoardMesh)
//
//            dotMesh = Mesh()
//            textBoardMesh = Mesh()

//            textBoardPicker = true
//            textBoardMesh.visible = true
//            loadDataset()
            updateInstancingColor()

            geneBoard.text = "Gene: " + geneNames[genePicker]
        }

    }
}