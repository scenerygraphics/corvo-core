package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.numerics.Random
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.extensions.xyzw
import graphics.scenery.volumes.Colormap
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.math.ceil
import kotlin.properties.Delegates

/**.
 *
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */

class XPlot: Node() {

    val laser = Cylinder(0.01f, 2.0f, 20)
    val laser2 = Cylinder(0.01f, 2.0f, 20)

    //private var cellCount by Delegates.notNull<Int>()

    // define meshes that make up the scene
    var dotMesh = Mesh()
    var textBoardMesh = Mesh()

    // set type of shape data is represented as + a scaling factor for better scale relative to user
    var positionScaling = 0.2f
    var globalMasterMap = hashMapOf<Int, Icosphere>()

    // variables that need to be accessed globally, but are defined in a limited namespace
    private var globalGeneCount by Delegates.notNull<Int>()

    // global as it is required by Visualization class
    var genePicker = 0
    var textBoardPicker = true
    val geneBoard = TextBoard()
    lateinit var cameraLight: PointLight
    val geneNames = ArrayList<String>()

    init {
        loadDataset()
    }

    private fun loadDataset() {

        val (spatialCoordinates, cellNames, geneIndexList) = AnnotationsIngest().fetchTriple(geneNames, listOf("Alg12", "Asf1b", "Cd3e", "Fbxo21", "Gm15800"))

        val geneReader = SparseReader()
        val geneExpression = ArrayList<FloatArray>()
        for(i in geneIndexList){
            geneExpression.add(geneReader.cscReader(i))
        }

        val uniqueCellNames = cellNames.toSet() as Set<String>

        // generate random color vector for each cell ontology
        val tabulaColorMap = HashMap<String, Vector3f>()
        for (i in uniqueCellNames) {
            tabulaColorMap[i] = Random.random3DVectorFromRange(0f, 1.0f)
        }

        // initialize gene color map from scenery.Colormap
        val colormap = Colormap.get("viridis")
        // "grays", "hot", "jet", "plasma", "viridis"

        /*
        Instancing
        - Create parent sphere that instances inherit from.
        - Instanced properties are position and color.
        - All instances and masters exist in dotMesh.
         */

        /*
        generate master spheres for every 10k cells for performance
         */
        val masterCount = ceil(cellNames.size.toFloat()/10000).toInt()
        if(masterCount==0){
            throw java.lang.IndexOutOfBoundsException("no cells could be found")
        }

        val masterMap = hashMapOf<Int, Icosphere>()

        // hashmap to emulate at run time variable declaration
        // allows for dynamically growing number of master spheres with size of dataset
        for (i in 1..masterCount){
            val masterTemp = Icosphere(0.05f * positionScaling, 2)
            masterMap[i] = addMasterProperties(masterTemp, i)
        }
        logger.info("hashmap looks like: $masterMap")

        // give access to hashmap of master objects to functions outside of init and to XVisualization class
        globalMasterMap = masterMap

        /*
        create and add instances using their UMAP coordinates as position and cell ontology or gene expression as color
         */
        var zipCounter = 0
        var resettingZipCounter = 0
        var parentIterator = 1

        cellNames.zip(spatialCoordinates) { cell, coord ->

            if(resettingZipCounter >= 10000){
                parentIterator += 1
                logger.info("parentIterator: $parentIterator")
                resettingZipCounter = 0
            }

            val s = Mesh()

            // index element zipCounter of every array of gene expressions and add to new ArrayList
            val indexedGeneExpression = ArrayList<Float>()
            for(gene in geneExpression){
                indexedGeneExpression.add(gene[zipCounter])
            }

            s.parent = masterMap[parentIterator]
            s.name = cell as String
            s.position = Vector3f(coord[0], coord[1], coord[2]) * positionScaling

            s.instancedProperties["ModelMatrix"] = { s.world }
            s.instancedProperties ["Color"] = {
            var color = if (textBoardPicker) {
                // cell type encoded as color
                tabulaColorMap.getOrDefault(cell, Vector3f(1.0f, 0f, 0f)).xyzw()

            } else {
                colormap.sample(indexedGeneExpression[genePicker]/10f)
                }

                // metadata "selected" stores whether point has been marked by laser. Colors marked cells red.
                (s.metadata["selected"] as? Boolean)?.let {
                    if (it) {
                        color = s.material.diffuse.xyzw()
                    }
                }
                color
            }

            masterMap[parentIterator]?.instances?.add(s)
            zipCounter += 1
            resettingZipCounter += 1
        }

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 1
        geneBoard.fontColor = Vector3f(0f, 0f, 0f).xyzw()
        geneBoard.position = Vector3f(-5f, 0f, -9f) // on far wall
        geneBoard.visible = false
//        geneBoard.position = Vector3f(0f, 0f, -0.2f)
//        geneBoard.scale = Vector3f(0.05f, 0.05f, 0.05f)
        geneBoard.scale = Vector3f(1f, 1f, 1f)
//
        addChild(geneBoard)
        // create cylinders orthogonal to each other, representing axes centered around 0,0,0 and add them to the scene
        val x = generateAxis("X", 5.00f)
        addChild(x)
        val y = generateAxis("Y", 5.00f)
        addChild(y)
        val z = generateAxis(
"Z", 5.00f)
        addChild(z)

        // add box to scene for sense of bound
        val lightbox = Box(Vector3f(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material.diffuse = Vector3f(0.4f, 0.4f, 0.4f)
        lightbox.material.roughness = 1.0f
        lightbox.material.metallic = 0.0f
        lightbox.material.cullingMode = Material.CullingMode.None
        addChild(lightbox)

        val lightStretch = 5f
        val lights = (0 until 8).map {
            val l = PointLight(radius = 95.0f)
            l.position = Vector3f(
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch),
                Random.randomFromRange(-lightStretch, lightStretch)
            )
//            l.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)
            l.emissionColor = Vector3f(0.4f, 0.4f, 0.4f)
            l.intensity = Random.randomFromRange(0.4f, 0.9f)

            l
        }

        lights.forEach { lightbox.addChild(it) }

        // light attached to the camera
        cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        cameraLight.intensity = 0.8f

        // give lasers texture and set them to be visible (could use to have different lasers/colors/styles and switch between them)
        initializeLaser(laser)
        initializeLaser(laser2)

        // fetch center of mass for each cell type and attach TextBoard with cell type at that location
        val massMap = textBoardPositions(uniqueCellNames)
        for(i in uniqueCellNames){
            val t = TextBoard(isBillboard = true)
            t.text = i
            t.name = i
            t.transparent = 0
            t.fontColor = Vector3f(0.05f, 0.05f, 0.05f).xyzw()
            t.backgroundColor = tabulaColorMap[i]!!.xyzw()
            t.position = massMap[i]!!
            t.scale = Vector3f(0.8f, 0.8f, 0.8f)*positionScaling
            textBoardMesh.addChild(t)
        }

        // add all data points and their labels (if color encodes cell type) to the scene
        addChild(textBoardMesh)
        addChild(dotMesh)
    }

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCenterOfMass(type: String): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for(i in globalMasterMap[1]!!.instances.filter{ it.name == type }) {
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
        for(i in cellNameSet){
            massMap[i] = fetchCenterOfMass(i)
            logger.info("center of mass for $i is: ${fetchCenterOfMass(i)}")
        }
        return massMap
    }

    private fun initializeLaser(laserName: Cylinder){
        laserName.material.diffuse = Vector3f(0.9f, 0.0f, 0.0f)
        laserName.material.metallic = 0.001f
        laserName.material.roughness = 0.18f
        laserName.rotation.rotateX(-Math.PI.toFloat()/1.5f) // point laser forwards
        laserName.visible = true
    }

    private fun generateAxis(dimension: String = "x", length: Float = 5.00f): Cylinder{
        val cyl: Cylinder = when(dimension.capitalize()){
            "X" -> { Cylinder.betweenPoints(Vector3f(-length, 0f, 0f), Vector3f(length, 0f, 0f), radius = 0.01f) }
            "Y" -> { Cylinder.betweenPoints(Vector3f(0f, -length, 0f), Vector3f(0f, length, 0f), radius = 0.01f) }
            "Z" -> { Cylinder.betweenPoints(Vector3f(0f, 0f, -length), Vector3f(0f, 0f, length), radius = 0.01f) }
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
        master.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("DefaultDeferredInstanced.frag", "DefaultDeferredInstancedColor.vert"), XPlot::class.java)) //overrides the shader
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
        for(i in 0..globalMasterMap.size){
            globalMasterMap[i]?.instances?.forEach {
                it.visible = true
                it.metadata.remove("selected")
            }
        }
    }

    fun reload() {
        children.forEach { child -> removeChild(child) }

        dotMesh = Mesh()
        textBoardMesh = Mesh()

        loadDataset()
    }
}