package graphics.scenery.dimensionalreduction

import graphics.scenery.*
import graphics.scenery.backends.Shaders
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.toFloatArray
import graphics.scenery.utils.extensions.xyzw
import graphics.scenery.numerics.Random.*
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.set
import kotlin.math.round
import kotlin.properties.Delegates

/**.
 *
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */

class TSNEPlot(val fileName: String = "GMB_cellAtlas_data.csv "): Node() {
    val laser = Cylinder(0.01f, 2.0f, 20)
    val laser2 = Cylinder(0.01f, 2.0f, 20)

    // define meshes that make up the scene
    var dotMesh = Mesh()
    var textBoardMesh = Mesh()

    // set type of shape data is represented as + a scaling factor for better scale relative to user
    var positionScaling = 0.2f
    var v = Icosphere(0.20f * positionScaling, 1)

    // variables that need to be accessed globally, but are defined in a limited namespace
    private lateinit var globalGeneExpression: ArrayList<ArrayList<Float>>
    private var globalGeneCount by Delegates.notNull<Int>()
    private lateinit var uniqueCellNames: Set<String>

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
        /**
         * main function to
         *
         */

        // calls csvReader function on chosen dataset and outputs cell names (+ its dataset name), gene expression data, and its coordinates in UMAP space to three arrays
        val (cellNames, geneExpressions, tsneCoordinates) = csvReader(fileName)

        // creates a set including each cell type once
        uniqueCellNames = cellNames.map { it.split("//")[1] }.toSet()

        // initializes global variable used in some functions and in TSNEVisualization class
        globalGeneExpression = geneExpressions

        // Choose colormap
        val colorMaps = getColorMaps()
        val defaultColor = "pancreaticCellMap"
        val colorMap = colorMaps[defaultColor]//deprecated - for old data set
        val tabulaColorMap = colorMaps["tabulaCells"]
        if (colorMap == null || tabulaColorMap == null) {
            throw IllegalStateException("colorMap not found") }

        // calls function that normalizes all gene expression values between 0 and 1
        val normGeneExp = normalizeGeneExpressions()

        val roundedColorMap = hashMapOf<Int, Vector3f>(

//                0 to Vector3f(62f/255f, 20f/255f, 81f/255f),
//                1 to Vector3f(66f/255f, 27f/255f, 100f/255f),
//                2 to Vector3f(64f/255f, 72f/255f, 132f/255f),
//                3 to Vector3f(62f/255f, 99f/255f, 138f/255f),
//                4 to Vector3f(63f/255f, 112f/255f, 139f/255f),
//                5 to Vector3f(68f/255f, 136f/255f, 140f/255f),
//                6 to Vector3f(78f/255f, 160f/255f, 135f/255f),
//                7 to Vector3f(117f/255f,195f/255f, 113f/255f),
//                8 to Vector3f(139f/255f, 205f/255f, 102f/255f),
//                9 to Vector3f(192f/255f, 220f/255f, 80f/255f),
//                10 to Vector3f(250f/255f, 231f/255f, 85f/255f)
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

        /*
        Instancing
        - Create parent sphere that instances inherit from.
        - Instanced properties are position and color.
        - All instances and parent exist in dotMesh.
         */
        v.name = "master sphere"
        v.material = ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("DefaultDeferredInstanced.frag", "DefaultDeferredInstancedColor.vert"), TSNEPlot::class.java)) //overrides the shader
        v.material.diffuse = Vector3f(0.8f, 0.7f, 0.7f)
        v.material.ambient = Vector3f(0.1f, 0.0f, 0.0f)
        v.material.specular = Vector3f(0.05f, 0f, 0f)
        v.material.roughness = 0.8f
        v.metadata["sourceCount"] = 2
        v.instancedProperties["ModelMatrix"] = { v.world }
        v.instancedProperties["Color"] = { v.material.diffuse.xyzw() }
        dotMesh.addChild(v)

        var zipCounter = 0
        cellNames.zip(tsneCoordinates) {cell, coord ->
            val s = Mesh()

            // cell name and data set index returned as single list delimited by //. Is split into separate lists
            val cellName = cell.split("//").getOrNull(1) ?: ""
            val cellSource = cell.split("//").getOrNull(0)?.toInt() ?: -1

            val parsedGeneExpressions = geneExpressions[zipCounter]
            val normParsedGeneExp = normGeneExp[zipCounter]

            s.parent = v
            s.name = cellName
            s.metadata["source"] = cellSource
            // gene expression has been normalized between 0 and 1. Expressions less than 0.2f is set constant for aesthetics
            s.scale = Vector3f(
                    (if((normParsedGeneExp[2]) < 0.2f){0.2f} else{(normParsedGeneExp[2])}),
                    (if((normParsedGeneExp[3]) < 0.2f){0.2f} else{(normParsedGeneExp[3])}),
                    (if((normParsedGeneExp[4]) < 0.2f){0.2f} else{(normParsedGeneExp[4])}))
            s.position = Vector3f(coord[0], coord[1], coord[2])*positionScaling

            s.instancedProperties["ModelMatrix"] = { s.world }
            s.instancedProperties["Color"] = {
                var color = if(textBoardPicker) {
                    tabulaColorMap.getOrDefault(cellName, Vector3f(1.0f, 0f, 0f)).xyzw()
                    // cel type encoded as color
                } else {
                    roundedColorMap[(normParsedGeneExp[genePicker]*10).toInt()]?.xyzw() ?: Vector4f(250f/255f, 231f/255f, 85f/255f, 1.0f)
                    // gene expression encoded as color
                    //roundedColorMap[(1/(7*(1.0f + log10(0.1f + parsedGeneExpressions[genePicker])))).toInt()]?.xyzw() ?: Vector4f(255f/255f, 255f/255f, 255f/255f, 1.0f)
                }
                // metadata "selected" stores whether point has been marked by laser. Colors marked cells red.
                (s.metadata["selected"] as? Boolean)?.let {
                    if(it) {
                        color = s.material.diffuse.xyzw()
                    }
                }

//                val source = s.metadata["source"] as? Int
//                if(source != null && source != currentDatasetIndex) {
//                    color = Vector4f(0.5f, 0.5f, 0.5f, 1.0f)
//                }

                // Uncommenting causes color loss for some clusters - debug

                color
            }
            v.instances.add(s)
            zipCounter += 1
        }

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 0
        geneBoard.fontColor = Vector3f(20f, 20f, 20f).xyzw()
        geneBoard.backgroundColor = Vector3f(1.0f, 1.0f, 1.0f).xyzw()
        //geneBoard.position = Vector3f(-15f, -10f, -48f) // on far wall
        geneBoard.scale = Vector3f(0.1f, 0.1f, 0.1f)
        geneBoard.visible = false
        geneBoard.update.add {
            val cam = getScene()?.findObserver() ?: return@add
            geneBoard.position = cam.viewportToWorld(Vector2f(0.7f, 0.85f))
            //geneBoard.position = cam.viewportToWorld(Vector2f(0.7f, 0.85f), 1.0f) + cam.forward * 0.8f
            geneBoard.rotation = if (cam is DetachedHeadCamera) {
                cam.headOrientation.conjugate().normalize()
            } else {
                cam.rotation.conjugate().normalize()
            }
        }
        addChild(geneBoard)

        // create cylinders orthogonal to each other, representing axes centered around 0,0,0 and add them to the scene
        val x = generateAxis("X", 5.00f)
        addChild(x)
        val y = generateAxis("Y", 5.00f)
        addChild(y)
        val z = generateAxis("Z", 5.00f)
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
        val massMap = textBoardPositions()
        for(i in uniqueCellNames){
            val t = TextBoard(isBillboard = false)
            t.text = i
            t.name = i
            t.transparent = 0
            t.fontColor = Vector3f(0.0f, 0.0f, 0.0f).xyzw()
            t.backgroundColor = tabulaColorMap[i]!!.xyzw()
            t.position = massMap[i]!!
            t.scale = Vector3f(0.4f, 0.4f, 0.4f)*positionScaling
            textBoardMesh.addChild(t)
        }

        // add all data points and their possible labels to the scene
        addChild(textBoardMesh)
        addChild(dotMesh)
    }

    private fun csvReader(pathName: String = "GMB_cellAtlas_data.csv "): Triple<ArrayList<String>, ArrayList<ArrayList<Float>>, ArrayList<ArrayList<Float>>> {
        val cellNames = ArrayList<String>()
        val geneExpressions = ArrayList<ArrayList<Float>>()
        val tsneCoordinates = ArrayList<ArrayList<Float>>()

        val csv = File(pathName)
        logger.info("pathname $pathName")
        var nline = 0

        csv.forEachLine(Charsets.UTF_8) {line ->
            if(nline == 0){
                line.split(",").drop(2).dropLast(4).forEach {
                    geneNames.add(it)
                }
            }
            else if(nline != 0) {
                val tsneFields = ArrayList<Float>()
                val geneFields = ArrayList<Float>()
                var colN = 0
                var cellName = ""
                var index = -1

                line.split(",").drop(1).dropLast(4).forEach{
                    if(colN == 0 ){
                        // cell name
                        cellName = it.replace("\"", "")
                    }
                    else{
                        // gene expression
                        val itFloatGene = (round(it.toFloat()*10f))/10f
                        geneFields.add(itFloatGene)
                    }
                    colN += 1
                }

                if(nline < 2) {
                    globalGeneCount = colN - 1
                }

                var coordinateCol = 0
                line.split(",").drop(1+colN).forEach{
                    if(coordinateCol < 3){
                        // coordinate
                        val itFloatTsne = it.toFloat()
                        tsneFields.add(itFloatTsne)
                    }
                    else{
                        // dataset
                        val name = it.replace("\"", "")
                        dataSet.add(name)
                        index = dataSet.indexOf(name)
                    }
                    coordinateCol += 1
                }

                geneExpressions.add(geneFields)
                tsneCoordinates.add(tsneFields)
                cellNames.add("$index//$cellName")
            }
            nline += 1
        }
        return Triple(cellNames, geneExpressions, tsneCoordinates)
    }

    private fun normalizeGeneExpressions(): ArrayList<ArrayList<Float>> {

        val normalizedGeneExpression = ArrayList<ArrayList<Float>>()
        val maxList = ArrayList<Float>()

        for(i in 0 until globalGeneCount){
            maxList.add(fetchMaxGeneExp(i))
        }

        for(row in globalGeneExpression){
            val subNormalized = ArrayList<Float>()
            var geneCounter = 0
            for(gene in row){
                subNormalized.add((round(gene/maxList[geneCounter]*10f))/10f)
                geneCounter += 1
            }
            normalizedGeneExpression.add(subNormalized)
        }

        return normalizedGeneExpression
    }

    private fun getColorMaps(): HashMap<String, HashMap<String, Vector3f>> {

        val tabulaCells = HashMap<String, Vector3f>()
        for (i in uniqueCellNames) {
            tabulaCells[i] = graphics.scenery.numerics.Random.random3DVectorFromRange(0f, 1.0f)
        }
        val pancreaticCellMap = hashMapOf(
            "udf" to Vector3f(255 / 255f, 98 / 255f, 188 / 255f),
            "type B pancreatic cell" to Vector3f(232 / 255f, 107 / 255f, 244 / 255f),
            "pancreatic stellate cell" to Vector3f(149 / 255f, 145 / 255f, 255 / 255f),
            "pancreatic PP cell" to Vector3f(0 / 255f, 176 / 255f, 246 / 255f),
            "pancreatic ductal cell" to Vector3f(0 / 255f, 191 / 255f, 196 / 255f),
            "pancreatic D cell" to Vector3f(0 / 255f, 190 / 255f, 125 / 255f),
            "pancreatic acinar cell" to Vector3f(57 / 255f, 182 / 255f, 0 / 255f),
            "pancreatic A cell" to Vector3f(162 / 255f, 165 / 255f, 0 / 255f),
            "leukocyte" to Vector3f(216 / 255f, 144 / 255f, 0 / 255f),
            "endothelial cell" to Vector3f(248 / 255f, 118 / 255f, 108 / 255f)
        )
        val plateMap = hashMapOf(
            "MAA000574" to Vector3f(102 / 255f, 194 / 255f, 165 / 255f),
            "MAA000577" to Vector3f(252 / 255f, 141 / 255f, 98 / 255f),
            "MAA000884" to Vector3f(141 / 255f, 160 / 255f, 203 / 255f),
            "MAA000910" to Vector3f(231 / 255f, 138 / 255f, 195 / 255f),
            "MAA001857" to Vector3f(166 / 255f, 216 / 255f, 84 / 255f),
            "MAA001861" to Vector3f(255 / 255f, 217 / 255f, 47 / 255f),
            "MAA001862" to Vector3f(229 / 255f, 196 / 255f, 148 / 255f),
            "MAA001868" to Vector3f(179 / 255f, 179 / 255f, 179 / 255f)
        )
        return hashMapOf(
            "pancreaticCellMap" to pancreaticCellMap,
            "plateMap" to plateMap,
            "tabulaCells" to tabulaCells,
        )
    }// Currently only works with random color map

    // for a given cell type, find the average position for all of its instances. Used to place label in sensible position, given that the data is clustered
    private fun fetchCenterOfMass(type: String): Vector3f {
        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for(i in v.instances.filter{ it.name == type }) {
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
    private fun textBoardPositions(): HashMap<String, Vector3f> {
        val massMap = HashMap<String, Vector3f>()
        for(i in uniqueCellNames){
            massMap[i] = fetchCenterOfMass(i)
            logger.info("center of mass for $i is: ${fetchCenterOfMass(i)}")
        }
        return massMap
    }

    private fun fetchMaxGeneExp(geneLocus: Int): Float {
        val maxList = ArrayList<Float>()
        // index chosen gene (geneLocus) from each row (cell) and add to list maxList
        for(i in globalGeneExpression){
            maxList.add(i[geneLocus])
        }
        // return highest gene expression from list
        val max = maxList.maxOrNull()
        return max!!
    }

    private fun initializeLaser(laserName: Cylinder){
        laserName.material.diffuse = Vector3f(5.0f, 0.0f, 0.02f)
        laserName.material.metallic = 0.5f
        laserName.material.roughness = 1.0f
        laser.rotation.rotateX(-Math.PI.toFloat()/1.5f) // point laser forwards
        laserName.visible = true
    }

    private fun generateAxis(dimension: String = "x", length: Float = 5.00f): Cylinder{
        val cyl: Cylinder = when(dimension.capitalize()){
            "X" -> { Cylinder.betweenPoints(Vector3f(-length, 0f, 0f), Vector3f(length, 0f, 0f)) }
            "Y" -> { Cylinder.betweenPoints(Vector3f(0f, -length, 0f), Vector3f(0f, length, 0f)) }
            "Z" -> { Cylinder.betweenPoints(Vector3f(0f, 0f, -length), Vector3f(0f, 0f, length)) }
            else -> throw IllegalArgumentException("$dimension is not a valid dimension")
        }
        cyl.material.diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        return cyl
    }

    fun cycleDatasets() {
        currentDatasetIndex = (currentDatasetIndex + 1) % dataSet.size
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