package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.extensions.xyzw
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
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

    var v = Icosphere(5f * positionScaling, 1) // default r: 0.2f - deprecated, still used for reset

    lateinit var globalMasterMap: HashMap<Int, Icosphere>

    // variables that need to be accessed globally, but are defined in a limited namespace
    private var globalGeneCount by Delegates.notNull<Int>()

    // global as it is required by Visualization class
    var genePicker = 0
    var textBoardPicker = true
    val geneBoard = TextBoard()
    val geneNames = ArrayList<String>()
    var currentDatasetIndex = 0
    var dataSet = HashSet<String>()

    init {
        loadDataset()
    }

    private fun loadDataset() {

        val geneNumber = 12
        val geneReader = SparseReader()
        val geneExpression = geneReader.cscReader(geneNumber)

        val geneName = AnnotationsIngest().h5adAnnotationReader("/var/index")[12]
        geneNames.add(geneName as String)

        val spatialCoordinates = AnnotationsIngest().UMAPReader3D()

        val cellNames = AnnotationsIngest().h5adAnnotationReader("/obs/cell_ontology_class")
        // creates a set including each cell type once
        val uniqueCellNames = cellNames.toSet() as Set<String>

        // initializes global variable used in some functions and in TSNEVisualization class

        val tabulaColorMap = HashMap<String, Vector3f>()
        for (i in uniqueCellNames) {
            tabulaColorMap[i] = graphics.scenery.numerics.Random.random3DVectorFromRange(0f, 1.0f)
        }

        // calls function that normalizes all gene expression values between 0 and 1

        val roundedColorMap = hashMapOf(
            0 to Vector3f(247f/255f, 252f/255f, 253f/255f),
            1 to Vector3f(229f/255f, 245f/255f, 249f/255f),
            2 to Vector3f(204f/255f, 236f/255f, 230f/255f),
            3 to Vector3f(153f/255f,216f/255f, 201f/255f),
            4 to Vector3f(102f/255f, 194f/255f, 164f/255f),
            5 to Vector3f(65f/255f, 174f/255f, 118f/255f),
            6 to Vector3f(35f/255f, 139f/255f, 69f/255f),
            7 to Vector3f(0f/255f, 109f/255f, 44f/255f),
            8 to Vector3f(0f/255f, 68f/255f, 27f/255f),
            9 to Vector3f(0f/255f, 34f/255f, 13f/255f),
            10 to Vector3f(0f/255f, 17f/255f, 6f/255f)
        )

        println(roundedColorMap[geneExpression[2000].toInt()])
        /*
        Instancing
        - Create parent sphere that instances inherit from.
        - Instanced properties are position and color.
        - All instances and parent exist in dotMesh.
         */

        val numCells = cellNames.size.toFloat()
        val masterCount = ceil(numCells/10000).toInt()

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

            val parsedTestGeneExpression = geneExpression[zipCounter]

            s.parent = masterMap[parentIterator]
            s.name = cell as String
            s.position = Vector3f(coord[0], coord[1], coord[2]) * positionScaling

            s.instancedProperties["ModelMatrix"] = { s.world }
            s.instancedProperties ["Color"] = {
            var color = if (textBoardPicker) {
                // cell type encoded as color
                tabulaColorMap.getOrDefault(cell, Vector3f(1.0f, 0f, 0f)).xyzw()
            } else {
                // gene expression encoded as color
                roundedColorMap[parsedTestGeneExpression.toInt()]?.xyzw() ?: Vector4f(
                    250f / 255f,
                    231f / 255f,
                    0f / 255f,
                    1.0f
                    )
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
        geneBoard.backgroundColor = Vector3f(1.0f, 1.0f, 1.0f).xyzw()
        //geneBoard.position = Vector3f(-15f, -10f, -48f) // on far wall
        geneBoard.scale = Vector3f(0.1f, 0.1f, 0.1f)
        geneBoard.visible = false
        geneBoard.position = Vector3f(0f, 0f, -0.2f)
        geneBoard.scale = Vector3f(0.05f, 0.05f, 0.05f)


        // create cylinders orthogonal to each other, representing axes centered around 0,0,0 and add them to the scene
        val x = generateAxis("X", 5.00f)
        addChild(x)
        val y = generateAxis("Y", 5.00f)
        addChild(y)
        val z = generateAxis(
"Z", 5.00f)
        addChild(z)

        // create scene lighting
        Light.createLightTetrahedron<PointLight>(spread = 10.0f, radius = 95.0f).forEach {
            addChild(it)
        }

        // add box to scene for sense of bound
        val hullbox = Box(Vector3f(100.0f, 100.0f, 100.0f), insideNormals = true)
        with(hullbox) {
            position = Vector3f(0.0f, 0.0f, 0.0f)
            material.ambient = Vector3f(0.6f, 0.6f, 0.6f)
            material.diffuse = Vector3f(0.4f, 0.4f, 0.4f)
            material.specular = Vector3f(0.0f, 0.0f, 0.0f)
            material.cullingMode = Material.CullingMode.Front
        }
        addChild(hullbox)

        // give lasers texture and set them to be visible (could use to have different lasers/colors/styles and switch between them)
        initializeLaser(laser)
        initializeLaser(laser2)

        // fetch center of mass for each cell type and attach TextBoard with cell type at that location
        val massMap = textBoardPositions(uniqueCellNames)
        for(i in uniqueCellNames){
            val t = TextBoard(isBillboard = false)
            t.text = i
            t.name = i
            t.transparent = 1
            t.fontColor = Vector3f(0.0f, 0.0f, 0.0f).xyzw()
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
        laserName.material.diffuse = Vector3f(5.0f, 0.0f, 0.02f)
        laserName.material.metallic = 0.5f
        laserName.material.roughness = 1.0f
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
        master.material.diffuse = Vector3f(0.8f, 0.7f, 0.7f)
        master.material.ambient = Vector3f(0.1f, 0.0f, 0.0f)
        master.material.specular = Vector3f(0.05f, 0f, 0f)
        master.material.roughness = 0.2f
        master.metadata["sourceCount"] = 2
        master.instancedProperties["ModelMatrix"] = { master.world }
        master.instancedProperties["Color"] = { master.material.diffuse.xyzw() }

        dotMesh.addChild(master)

        return master
    }

    fun resetVisibility() {
        v.instances.forEach {
            it.visible = true
            it.metadata.remove("selected")
        }
    }

    fun reload() {
        children.forEach { child -> removeChild(child) }

        v = Icosphere(0.2f * positionScaling, 1)
        dotMesh = Mesh()
        textBoardMesh = Mesh()

        loadDataset()
    }
}