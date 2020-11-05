package graphics.scenery

import cleargl.GLVector
import java.io.File
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.set
import kotlin.math.log10
import kotlin.math.round


/**.
 *
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */

class TSNEPlot(val fileName: String = "GMB_cellAtlas_data.csv "): Node() {
    val laser = Cylinder(0.01f, 2.0f, 20)
    val laser2 = Cylinder(0.01f, 2.0f, 20)

    var mesh = Mesh()
    var dotMesh = Mesh()
    var textBoardMesh = Mesh()

    var positionScaling = 0.2f
    var v = Icosphere(0.20f * positionScaling, 1)


    private lateinit var globalGeneExpression: ArrayList<ArrayList<Float>>
    private lateinit var uniqueCellNames: Set<String>
    var dataSet = HashSet<String>()

    var genePicker = 0
    var textBoardPicker = true

    val geneBoard = TextBoard()
    val geneNames = ArrayList<String>()


    var currentDatasetIndex = 0

    init {
        loadDataset()
    }

    fun reload() {
        children.forEach { child -> removeChild(child) }

        v = Icosphere(0.2f * positionScaling, 1)
        dotMesh = Mesh()
        textBoardMesh = Mesh()
        mesh = Mesh()

        loadDataset()
    }

    private fun loadDataset() {

        val (cellNames, geneExpressions, tsneCoordinates) = csvReader(fileName)
        uniqueCellNames = cellNames.map { it.split("//")[1] }.toSet()
        globalGeneExpression = geneExpressions

        // Parameters
        val colorMaps = getColorMaps()
        val defaultColor = "pancreaticCellMap"
        val defaultShape = "ellipses"
        val colorMap = colorMaps[defaultColor]//deprecated - for old data set
        val tabulaColorMap = colorMaps["tabulaCells"]
        if (colorMap == null || tabulaColorMap == null) {
            throw IllegalStateException("colorMap not found") }


        val normalizedGeneExpression = ArrayList<ArrayList<Float>>()
        val maxList = ArrayList<Float>()
        for(i in 0..9){
            maxList.add(fetchMaxGeneExp(i))
        }

        for(row in geneExpressions){
            val subNormalized = ArrayList<Float>()
            var geneCounter = 0
            for(gene in row){
                subNormalized.add((round(gene/maxList[geneCounter]*10f))/10f)
                geneCounter += 1
            }
            normalizedGeneExpression.add(subNormalized)
        }

        val roundedColorMap = hashMapOf<Int, GLVector>(
                0 to GLVector(62f/255f, 20f/255f, 81f/255f),
                1 to GLVector(66f/255f, 27f/255f, 100f/255f),
                2 to GLVector(64f/255f, 72f/255f, 132f/255f),
                3 to GLVector(62f/255f, 99f/255f, 138f/255f),
                4 to GLVector(63f/255f, 112f/255f, 139f/255f),
                5 to GLVector(68f/255f, 136f/255f, 140f/255f),
                6 to GLVector(78f/255f, 160f/255f, 135f/255f),
                7 to GLVector(117f/255f,195f/255f, 113f/255f),
                8 to GLVector(139f/255f, 205f/255f, 102f/255f),
                9 to GLVector(192f/255f, 220f/255f, 80f/255f),
                10 to GLVector(250f/255f, 231f/255f, 85f/255f)
        )
        //Color map options: https://cran.r-project.org/web/packages/viridis/vignettes/intro-to-viridis.html, https://github.com/sjmgarnier/viridisLite/tree/master/data-raw\


        v.name = "master sphere"
        v.material = ShaderMaterial.fromFiles("DefaultDeferredInstancedColor.vert", "DefaultDeferredInstanced.frag")
        //overrides the shader
        v.material.diffuse = GLVector(0.8f, 0.7f, 0.7f).xyzw()
        v.material.ambient = GLVector(0.1f, 0.0f, 0.0f)
        v.material.specular = GLVector(0.05f, 0f, 0f)
        v.material.roughness = 0.8f
        v.metadata["sourceCount"] = 2
        v.instancedProperties["ModelMatrix"] = { v.world }
        v.instancedProperties["Color"] = { v.material.diffuse }
        dotMesh.addChild(v)

        var zipCounter = 0
        cellNames.zip(tsneCoordinates) {cell, coord ->
            val s = Mesh()
            val cellName = cell.split("//").getOrNull(1) ?: ""
            val cellSource = cell.split("//").getOrNull(0)?.toInt() ?: -1

            val parsedGeneExpressions = geneExpressions[zipCounter]
            logger.info("gene expression values: ${parsedGeneExpressions}")
            val normalizedParsedGeneExpressions = normalizedGeneExpression[zipCounter]
            print(normalizedParsedGeneExpressions)
            s.parent = v

            s.name = cellName
            s.metadata["source"] = cellSource
            s.scale = GLVector(
                    (if((normalizedParsedGeneExpressions[2]) < 0.2f){0.2f} else{(normalizedParsedGeneExpressions[2])}),
                    (if((normalizedParsedGeneExpressions[3]) < 0.2f){0.2f} else{(normalizedParsedGeneExpressions[3])}),
                    (if((normalizedParsedGeneExpressions[4]) < 0.2f){0.2f} else{(normalizedParsedGeneExpressions[4])}))

            s.position = GLVector(coord[0], coord[1], coord[2])*positionScaling
            logger.info("x coordinate is: ${coord[0]}")

            s.instancedProperties["ModelMatrix"] = { s.world }
            s.instancedProperties["Color"] = {
                var color = if(textBoardPicker) {
                    tabulaColorMap.getOrDefault(cellName, GLVector(1.0f, 0f, 0f)).xyzw()
                } else {
                    //roundedColorMap[(normalizedParsedGeneExpressions[genePicker]*10).toInt()]?.xyzw() ?: GLVector(250f/255f, 231f/255f, 85f/255f, 1.0f)
                    roundedColorMap[(1/(7*(1.0f + log10(0.1f + parsedGeneExpressions[genePicker])))).toInt()]?.xyzw() ?: GLVector(250f/255f, 231f/255f, 85f/255f, 1.0f)
                }

                (s.metadata["selected"] as? Boolean)?.let {
                    if(it) {
                        color = s.material.diffuse
                    }
                }

//                val source = s.metadata["source"] as? Int
//                if(source != null && source != currentDatasetIndex) {
//                    color = GLVector(0.5f, 0.5f, 0.5f, 1.0f)
//                }

                color
            }
            v.instances.add(s)
            zipCounter += 1
        }

        geneBoard.transparent = 0
        geneBoard.fontColor = GLVector(0.0f, 0.0f, 0.0f)
        geneBoard.backgroundColor = GLVector(1.0f, 1.0f, 1.0f)
        //geneBoard.position = GLVector(-15f, -10f, -48f)
        geneBoard.scale = GLVector(0.1f, 0.1f, 0.1f)
        geneBoard.visible = false
        geneBoard.update.add {
            val cam = getScene()?.findObserver() ?: return@add

            geneBoard.position = cam.viewportToWorld(GLVector(0.7f, 0.85f), 1.0f) + cam.forward * 0.8f
            geneBoard.rotation = if (cam is DetachedHeadCamera) {
                cam.headOrientation.conjugate().normalize()
            } else {
                cam.rotation.conjugate().normalize()
            }
        }
        addChild(geneBoard)

        val x = Cylinder.betweenPoints(GLVector(-5.00f, 0f, 0f), GLVector(5.00f, 0f, 0f))
        x.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        val y = Cylinder.betweenPoints(GLVector(0f, -5.00f, 0f), GLVector(0f, 5.00f, 0f))
        y.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)
        val z = Cylinder.betweenPoints(GLVector(0f, 0f, -5.00f), GLVector(0f, 0f, 5.00f))
        z.material.diffuse = GLVector(1.0f, 1.0f, 1.0f)

        // Add objects to the scene
        addChild(x)
        addChild(y)
        addChild(z)// Cylinders
        addChild(mesh)

        // Create scene lighting
        Light.createLightTetrahedron<PointLight>(spread = 10.0f, radius = 95.0f).forEach {
            addChild(it)
        }

        //Add box to scene for sense of bound
        val hullbox = Box(GLVector(100.0f, 100.0f, 100.0f), insideNormals = true)
        with(hullbox) {
            position = GLVector(0.0f, 0.0f, 0.0f)

            material.ambient = GLVector(0.6f, 0.6f, 0.6f)
            material.diffuse = GLVector(0.4f, 0.4f, 0.4f)
            material.specular = GLVector(0.0f, 0.0f, 0.0f)
            material.cullingMode = Material.CullingMode.Front
        }
        addChild(hullbox)//hullbox

        laser.material.diffuse = GLVector(5.0f, 0.0f, 0.02f)
        laser.material.metallic = 0.5f
        laser.material.roughness = 1.0f
        laser.rotation.rotateByAngleX(-Math.PI.toFloat()/1.5f)
        laser.visible = true//laser

        laser2.material.diffuse = GLVector(0.01f, 0.0f, 5.0f)
        laser2.material.metallic = 0.5f
        laser2.material.roughness = 1.0f
        laser2.rotation.rotateByAngleX(-Math.PI.toFloat()/1.5f)
        laser2.visible = true//laser

        //fetch center of mass for each cell type and attach TextBoard with cell type at that location

        val listOfCells = arrayListOf(
                "udf",
                "type B pancreatic cell",
                "pancreatic stellate cell",
                "pancreatic PP cell",
                "pancreatic ductal cell",
                "pancreatic D cell",
                "pancreatic acinar cell",
                "pancreatic A cell",
                "leukocyte",
                "endothelial cell")//reduced list of cell types - not used in current build

        val massMap = textBoardPositions()

        for(i in uniqueCellNames){
            val t = TextBoard(isBillboard = false)
            t.text = i
            t.name = i
            t.transparent = 0
            t.fontColor = GLVector(0.0f, 0.0f, 0.0f)
            t.backgroundColor = tabulaColorMap[i]!!
            t.position = massMap[i]!!
            t.scale = GLVector(0.4f, 0.4f, 0.4f)*positionScaling
            t.opacity = 0.5f
            textBoardMesh.addChild(t)
        }

        addChild(textBoardMesh)
        addChild(dotMesh)
        logger.info("my gene names are: $geneNames")
        v.notifyUpdate()
    }

    fun csvReader(pathName: String = "GMB_cellAtlas_data.csv "): Triple<ArrayList<String>, ArrayList<ArrayList<Float>>, ArrayList<ArrayList<Float>>> {
        val cellNames = ArrayList<String>()
        val geneExpressions = ArrayList<ArrayList<Float>>()
        val tsneCoordinates = ArrayList<ArrayList<Float>>()

        val csv = File(pathName)
        logger.info("pathname $pathName")
        var nline = 0
        csv.forEachLine(Charsets.UTF_8) {line ->
            if(nline != 0) {
                val tsneFields = ArrayList<Float>()
                val geneFields = ArrayList<Float>()
                //val roundedGeneFields = ArrayList<Float>()
                var colN = 0
                var cellName = ""
                var index = -1

                line.split(";").drop(1).forEach {
                    if(colN == 0) {
                        cellName = it.replace("\"", "")
                        logger.info("cell type: $it")
                    }
                    else if(colN in 1..10) {
                        //val itFloatGene = it.toFloat()
                        //geneFields.add(itFloatGene)

                        val itFloatGene = (round(it.toFloat()*10f))/10f
                        geneFields.add(itFloatGene)
                    }
                    else if(colN in 11..13) {
                        val itFloatTsne = it.toFloat()
                        tsneFields.add(itFloatTsne)
                    }
                    else if(colN ==14) {
                        val name = it.replace("\"", "")
                        dataSet.add(name)
                        index = dataSet.indexOf(name)
                        logger.info("dataSet result: $it")
                    }
                    colN += 1
                }
                logger.info("gene expression is: $geneFields")
                logger.info("coordinates added are: $tsneFields")
                geneExpressions.add(geneFields)
                tsneCoordinates.add(tsneFields)
                cellNames.add("$index//$cellName")
                //roundedGeneExpression.add(roundedGeneFields)
            }
            else if(nline == 0){
                line.split(";").drop(2).dropLast(4).forEach {
                    geneNames.add(it)
                }
            }
            nline += 1
        }
        return Triple(cellNames, geneExpressions, tsneCoordinates)
    }//read in tabula muris data//csvReader


    fun getColorMaps(): HashMap<String, HashMap<String, GLVector>> {

        val tabulaCells = HashMap<String, GLVector>()
        for(i in uniqueCellNames){
            tabulaCells[i] = graphics.scenery.numerics.Random.randomVectorFromRange(3, 0f, 1.0f)
        }

        val pancreaticCellMap = hashMapOf(
                "udf" to GLVector(255 / 255f, 98 / 255f, 188 / 255f),
                "type B pancreatic cell" to GLVector(232 / 255f, 107 / 255f, 244 / 255f),
                "pancreatic stellate cell" to GLVector(149 / 255f, 145 / 255f, 255 / 255f),
                "pancreatic PP cell" to GLVector(0 / 255f, 176 / 255f, 246 / 255f),
                "pancreatic ductal cell" to GLVector(0 / 255f, 191 / 255f, 196 / 255f),
                "pancreatic D cell" to GLVector(0 / 255f, 190 / 255f, 125 / 255f),
                "pancreatic acinar cell" to GLVector(57 / 255f, 182 / 255f, 0 / 255f),
                "pancreatic A cell" to GLVector(162 / 255f, 165 / 255f, 0 / 255f),
                "leukocyte" to GLVector(216 / 255f, 144 / 255f, 0 / 255f),
                "endothelial cell" to GLVector(248 / 255f, 118 / 255f, 108 / 255f)
        )

        val plateMap = hashMapOf(
                "MAA000574" to GLVector(102 / 255f, 194 / 255f, 165 / 255f),
                "MAA000577" to GLVector(252 / 255f, 141 / 255f, 98 / 255f),
                "MAA000884" to GLVector(141 / 255f, 160 / 255f, 203 / 255f),
                "MAA000910" to GLVector(231 / 255f, 138 / 255f, 195 / 255f),
                "MAA001857" to GLVector(166 / 255f, 216 / 255f, 84 / 255f),
                "MAA001861" to GLVector(255 / 255f, 217 / 255f, 47 / 255f),
                "MAA001862" to GLVector(229 / 255f, 196 / 255f, 148 / 255f),
                "MAA001868" to GLVector(179 / 255f, 179 / 255f, 179 / 255f)
        )

        val hashDatabase = hashMapOf(
                "pancreaticCellMap" to pancreaticCellMap,
                "plateMap" to plateMap,
                "tabulaCells" to tabulaCells
        )

        return hashDatabase
    }//get color maps

    fun getShapeMaps(): HashMap<String, Node>{

        return hashMapOf(
                "deltahedra" to Icosphere(0.20f * positionScaling, 0),
                "ellipses" to Icosphere(0.20f * positionScaling, 3),
                "cubes" to Box(GLVector(0.01f * positionScaling, 0.01f * positionScaling, 0.01f * positionScaling))
        )
    }//get shape maps//get shape maps - not used in current build

    fun fetchCenterOfMass(type: String): GLVector {

        val additiveMass = FloatArray(3)
        var filteredLength = 0f

        for(i in v.instances.filter{ it.name == type }) {

            additiveMass[0] += i.position.toFloatArray()[0]
            additiveMass[1] += i.position.toFloatArray()[1]
            additiveMass[2] += i.position.toFloatArray()[2]

            filteredLength += 1
        }

        return GLVector(
                (additiveMass[0] / filteredLength),
                (additiveMass[1] / filteredLength),
                (additiveMass[2] / filteredLength)
        )
    }//fetchCenterOfMass

    fun textBoardPositions(): HashMap<String, GLVector> {
        val massMap = HashMap<String, GLVector>()
        for(i in uniqueCellNames){
            massMap[i] = fetchCenterOfMass(i)
            logger.info("center of mass for $i is: ${fetchCenterOfMass(i)}")
        }
        return massMap
    }//textBoardPositions

    fun fetchMaxGeneExp(geneLocus: Int): Float {
        val maxList = ArrayList<Float>()
        for(i in globalGeneExpression){
            maxList.add(i[geneLocus])
        }
        val max = maxList.max()
        return max!!
    }

    fun resetVisibility() {
        v.instances.forEach {
            it.visible = true
            it.metadata.remove("selected")
        }
    }

    fun cycleDatasets() {
        currentDatasetIndex = (currentDatasetIndex + 1) % dataSet.size
    }

}