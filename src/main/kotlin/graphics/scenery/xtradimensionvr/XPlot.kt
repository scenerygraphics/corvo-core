package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.numerics.Random
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.extensions.xyzw
import graphics.scenery.volumes.Colormap
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
    var genePicker = 0
    var textBoardPicker = true
    val geneBoard = TextBoard()
    val geneNames = ArrayList<String>()

    val colorMapScale = Box(Vector3f(5.0f, 1.0f, 0f))
    val maxTick = TextBoard()
    val minTick = TextBoard()

    val annotationFetcher = AnnotationsIngest()

    // give annotations you would like (maybe with checkboxes, allow to enter the names of their annotations)
    // list of annotations
    var annotationList =
        arrayOf("cell_ontology_class", "method", "mouse.id", "sex", "subtissue", "tissue", "louvain", "leiden")
    var annotationArray = ArrayList<FloatArray>()

    val spatialCoords = annotationFetcher.UMAPReader3D()
    val cellNames = annotationFetcher.h5adAnnotationReader("/obs/cell_ontology_class") as ArrayList<String>
    var geneExpr = annotationFetcher.fetchGeneExpression(geneNames)

    init {
        for (ann in annotationList) {
            annotationArray.add(run {
                val raw = annotationFetcher.h5adAnnotationReader("/obs/$ann", false) as ArrayList<Byte>
                val norm = FloatArray(raw.size) // array of zeros if annotation entries are all the same
                val max: Byte? = raw.maxOrNull()
                if (max != null && max > 0f) {
                    for (i in raw.indices)
                        norm[i] = raw[i].toFloat() / max
                }
                norm
            })
        }
    }

    //generate master spheres for every 10k cells for performance
    val masterCount = ceil(spatialCoords.size.toFloat() / 10000).toInt()
    val masterMap = hashMapOf<Int, Icosphere>()

    val uniqueCellNames = cellNames.toSet()

    // initialize gene color map from scenery.Colormap
    val encoding = "hot"
    val colormap = Colormap.get(encoding)

    val annotationColors = Colormap.get("jet")

    val indexedGeneExpression = ArrayList<Float>()
    val indexedAnnotations = ArrayList<Float>()

    init {
        loadEnvironment()
        loadDataset()
        updateInstancingColor()
    }

    private fun loadDataset() {
        // hashmap to emulate at run time variable declaration
        // allows for dynamically growing number of master spheres with size of dataset
        for (i in 1..masterCount) {
            val masterTemp = Icosphere(0.05f * positionScaling, 3)
            masterMap[i] = addMasterProperties(masterTemp, i)
        }
        println("hashmap looks like: $masterMap")
        // give access to hashmap of master objects to functions outside of init and to XVisualization class

        //create and add instances using their UMAP coordinates as position
        var cellCounter = 0
        var resettingCellCounter = 0
        var parentIterator = 1

        for (coord in spatialCoords) {
            if (resettingCellCounter >= 10000) {
                parentIterator += 1
                logger.info("parentIterator: $parentIterator")
                resettingCellCounter = 0
            }

            val s = Mesh()

            s.parent = masterMap[parentIterator]
            s.position = Vector3f(coord[0], coord[1], coord[2]) * positionScaling

            s.instancedProperties["ModelMatrix"] = { s.world }

            masterMap[parentIterator]?.instances?.add(s)
            cellCounter += 1
            resettingCellCounter += 1
        }

        // fetch center of mass for each cell type and attach TextBoard with cell type at that location
        val massMap = textBoardPositions(uniqueCellNames)
        for (i in uniqueCellNames) {
            val t = TextBoard(isBillboard = true)
            t.text = i
            t.name = i
            t.transparent = 0
            t.fontColor = Vector3f(0.05f, 0.05f, 0.05f).xyzw()
//            t.backgroundColor = tabulaColorMap[i]!!.xyzw()

            t.position = massMap[i]!!
            t.scale = Vector3f(0.3f, 0.3f, 0.3f) * positionScaling
            textBoardMesh.addChild(t)
        }

        // add all data points and their labels (if color encodes cell type) to the scene
        addChild(textBoardMesh)
        addChild(dotMesh)
    }

    fun updateInstancingColor() {
        var counter = 0
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
            indexedAnnotations.clear()

            // index element counter of every array of gene expressions and add to new ArrayList
            for (gene in geneExpr)
                indexedGeneExpression += gene[counter]

            for (annotation in annotationArray)
                indexedAnnotations += annotation[counter]

            var color = if (textBoardPicker) {
                annotationColors.sample(indexedAnnotations[genePicker])

            } else {
                colormap.sample(indexedGeneExpression[genePicker] / 10f)
            }
            // metadata "selected" stores whether point has been marked by laser. Colors marked cells red.
            if (s.metadata["selected"] == true)
                color = s.material.diffuse.xyzw()

            s.instancedProperties["Color"] = { color }

            counter += 1
            resettingZipCounter += 1
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
        lightbox.material.cullingMode = Material.CullingMode.None
        addChild(lightbox)

        val lightStretch = 12.4f
        val lights = (0 until 12).map {
            val l = PointLight(radius = 200.0f)
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

        lights.forEach { lightbox.addChild(it) }

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 1
        geneBoard.fontColor = Vector3f(0f, 0f, 0f).xyzw()
        geneBoard.position = Vector3f(-2.5f, 1.5f, -12.4f) // on far wall
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

        colorMapScale.material.textures["diffuse"] =
            Texture.fromImage(Image.fromResource("volumes/colormap-$encoding.png", this::class.java))
        colorMapScale.material.metallic = 0.3f
        colorMapScale.material.roughness = 0.9f
        colorMapScale.position = Vector3f(0f, 3f, -12.4f)
        colorMapScale.visible = false
        addChild(colorMapScale)

        minTick.text = "0"
        minTick.transparent = 1
        minTick.fontColor = Vector3f(0.03f, 0.03f, 0.03f).xyzw()
        minTick.position = Vector3f(-2.5f, 3.5f, -12.4f)
        minTick.visible = false
        addChild(minTick)

        maxTick.text = "10"
        maxTick.transparent = 1
        maxTick.fontColor = Vector3f(0.03f, 0.03f, 0.03f).xyzw()
        maxTick.position = Vector3f(2.1f, 3.5f, -boxSize / 2 + 0.1f)
        maxTick.visible = false
        addChild(maxTick)
    }

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCenterOfMass(type: String): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for (i in masterMap[1]!!.instances.filter { it.name == type }) {
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
        for (i in 0..masterMap.size) {
            masterMap[i]?.instances?.forEach {
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

            geneExpr.clear()
            geneExpr = annotationFetcher.fetchGeneExpression(geneNames)

            textBoardPicker = false

            colorMapScale.visible = true
            maxTick.visible = true
            minTick.visible = true

            updateInstancingColor()
            geneBoard.text = "Gene: " + geneNames[genePicker]
        }
    }
}
