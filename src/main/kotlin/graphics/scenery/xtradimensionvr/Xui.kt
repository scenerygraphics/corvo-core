package graphics.scenery.xtradimensionvr

import graphics.scenery.Box
import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.TextBoard
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.xyzw
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.sqrt

class Xui(private val parent: XVisualization) {
    val transcription = TextBoard("SourceSansPro-Light.ttf")
    val micButton = Box(Vector3f(0.015f, 0f, 0.015f))
    val genesToLoad = Mesh()
    val testLabel = TextBoard("SourceSansPro-Light.ttf")

    val requestedGenesNames = ArrayList<String>()
    val requestedGenesIndices = ArrayList<Int>()

    var geneTagMesh = Mesh()

    var categoryLabel = TextBoard("SourceSansPro-Light.ttf")

//    val resetUI = arrayListOf(TextBoard("SourceSansPro-Light.ttf"), Icosphere(0.02f, 3))
//    val switchSelectionModeUI = arrayListOf(TextBoard("SourceSansPro-Light.ttf"), Icosphere(0.02f, 3))
//    val loadGenes = arrayListOf(TextBoard("SourceSansPro-Light.ttf"), Icosphere(0.02f, 3))

    var switchSelectionModeUIState = 0

    private val scale = 0.02f
    private val triSize = 0.065f
    private val triOffset = 0.1f

    val resetUI = Mesh()
    val switchSelectionModeUI = Mesh()
    val loadGenesUI = Mesh()

    private val meshList = arrayListOf(resetUI, switchSelectionModeUI, loadGenesUI)

    init {
        for (mesh in meshList) {

            val sphere = Icosphere(scale, 3)
            val board = TextBoard("SourceSansPro-Light.ttf")

            sphere.material.diffuse = Vector3f(0.5f)
            sphere.material.ambient = Vector3f(0.3f)
            sphere.material.specular = Vector3f(0.1f)
            sphere.material.roughness = 0.1f
            sphere.material.metallic = 0.000001f

            board.scale = Vector3f(scale)
            board.rotation.rotateX(-Math.PI.toFloat() / 2f)
            board.transparent = 0
            board.fontColor = Vector4f(0f)
            board.backgroundColor = Vector4f(0.7f)

            mesh.addChild(board)
            mesh.addChild(sphere)
            mesh.visible = true
        }
    }

    init {
//        genesToLoad.visible = true
        val labelList = arrayListOf(testLabel, transcription, categoryLabel)

        for (label in labelList) {
            label.transparent = 0
            label.fontColor = Vector4f(0f)
            label.backgroundColor = Vector4f(0.7f)
            label.scale = Vector3f(0.012f)
            label.rotation.rotateX(-Math.PI.toFloat() / 2f)
        }

        testLabel.text = "switch view"
        testLabel.position = Vector3f(0.005f, 0.009f, 0.017f)

        transcription.position = Vector3f(0.03f, 0.01f, 0.02f)

//        genesToLoad.position = Vector3f(0.03f, 0.01f, 0.03f)

        micButton.material.textures["diffuse"] =
            Texture.fromImage(Image.fromResource("volumes/mic_image.jpg", this::class.java))
        micButton.material.metallic = 0.3f
        micButton.material.roughness = 0.9f
        micButton.material.diffuse = Vector3f(0.5f)
        micButton.position = Vector3f(0f, 0.01f, 0.02f)

        // define features of category label (display selected category on controller)
        categoryLabel.scale = Vector3f(scale)
        categoryLabel.position = Vector3f(
            0.1f,
            0f,
            0f
        )

        dispMainUI()
    }

    fun dispMainUI() {
        (resetUI.children.first() as TextBoard).text = "reset"
        (switchSelectionModeUI.children.first() as TextBoard).text = "switch selector"
        (loadGenesUI.children.first() as TextBoard).text = "load genes"

        resetUI.children.first().position = Vector3f(-triSize + (scale / sqrt(2f)), -0.025f, triSize - triOffset - (scale / sqrt(2f)))
        resetUI.children.last().position = Vector3f(-triSize, -0.025f, triSize - triOffset)

        switchSelectionModeUI.children.first().position = Vector3f(triSize + (scale / sqrt(2f)), -0.025f, triSize - triOffset - (scale / sqrt(2f)))
        switchSelectionModeUI.children.last().position = Vector3f(triSize, -0.025f, triSize - triOffset)

        loadGenesUI.children.first().position = Vector3f((scale / sqrt(2f)), -0.025f, -triSize - triOffset - (scale / sqrt(2f)))
        loadGenesUI.children.last().position = Vector3f(0f, -0.025f, -triSize - triOffset)

    }

    /**
     * load the precomputer genes of a selected cluster as a mesh of textboards and attach it to the user's left
     * controller. Fetch the gene expression data for the pre-computer gene names
     **/
    fun dispGenes(selectedCluster: Int) {
        if (selectedCluster != -1 && !currentlyFetching) {
            currentlyFetching = true
            val geneIndices = ArrayList<Int>()

            parent.hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                if (device.value.role == TrackerRole.LeftHand) {
                    device.value.model?.removeChild(geneTagMesh)
                    device.value.model?.removeChild(categoryLabel)
                }
            }
            geneTagMesh.children.forEach {
                geneTagMesh.removeChild(it)
            }
            categoryLabel.text = parent.plot.annFetcher.categoryNames[annotationPicker][selectedCluster]

            val clusterData = parent.plot.annFetcher.precompGenesReader(annotationPicker, selectedCluster)

            if (clusterData.second.first.isNotEmpty()) {
                val backC =
                    ((selectedCluster.toFloat()) / (clusterData.first.toFloat() - 1)) * 0.99f

                for (i in 0 until clusterData.second.first[0].size) {

                    geneIndices.add(parent.plot.annFetcher.geneNames.indexOf(clusterData.second.first[0][i]))

                    val geneTag = TextBoard("SourceSansPro-Light.ttf")
                    geneTag.text =
                        clusterData.second.first[0][i] +
                                ", p: " + clusterData.second.second[0][i].toString() +
                                ", f.c.: " + clusterData.second.third[0][i].toString()

                    geneTag.scale = Vector3f(scale)
                    geneTag.position = Vector3f(
                        0.1f,
                        0f,
                        (i * scale) + (scale * 1.15f)
                    )
                    geneTag.rotation.rotateX(-Math.PI.toFloat() / 2f)
                    geneTag.transparent = 0
                    geneTag.fontColor = Vector4f(0f)
                    geneTag.backgroundColor = parent.plot.rgbColorSpectrum.sample(backC)

                    geneTagMesh.addChild(geneTag)
                }
            }

            (geneTagMesh.children.first() as TextBoard).fontFamily = "SourceSansPro-Semibold.ttf"

            parent.hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                if (device.value.role == TrackerRole.LeftHand) {
                    device.value.model?.addChild(geneTagMesh)
                    device.value.model?.addChild(categoryLabel)
                }
            }
            ////////////////////////////////////////

            val buffer = parent.plot.annFetcher.fetchGeneExpression(geneIndices)
            genePicker = 0
            geneNames.clear()
            geneExpr.clear()
            geneNames = buffer.first
            geneExpr = buffer.second
            maxExprList = buffer.third

            parent.plot.updateInstancingArrays()
            parent.geneBoard.text = "Gene: " + geneNames[genePicker]
            parent.maxTick.text = maxExprList[genePicker].toString()

            ///////////////////////////////////////
            currentlyFetching = false
        }
    }

    fun addDecodedGene(name: String) {
        requestedGenesNames.add(name)

        val geneLabel = TextBoard("SourceSansPro-Light.ttf")
        geneLabel.visible = true
        geneLabel.transparent = 0
        geneLabel.fontColor = Vector4f(0f)
        geneLabel.scale = Vector3f(0.012f)
        geneLabel.rotation.rotateX(-Math.PI.toFloat() / 2f)
        geneLabel.text = name

        val geneIndex = parent.plot.annFetcher.geneNames.indexOf(name)

        if (geneIndex != -1) {
            geneLabel.backgroundColor = Vector3f(0.73f, 1.00f, 0.60f).xyzw()
            requestedGenesIndices.add(geneIndex)
        } else {
            geneLabel.backgroundColor = Vector3f(1.00f, 0.73f, 0.60f).xyzw()
        }

        geneLabel.position = Vector3f(0.03f, 0.01f, 0.025f + (0.012f * (requestedGenesNames.size)))
        genesToLoad.addChild(geneLabel)
    }

}
