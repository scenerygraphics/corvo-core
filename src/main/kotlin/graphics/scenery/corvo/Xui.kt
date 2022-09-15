package graphics.scenery.corvo

import graphics.scenery.Box
import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.primitives.TextBoard
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

    val requestedGenesNames = ArrayList<String>()
    val requestedGenesIndices = ArrayList<Int>()

    var geneTagMesh = Mesh()

    var categoryLabel = TextBoard("SourceSansPro-Light.ttf")

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
            sphere.material {
                diffuse = Vector3f(0.5f)
                ambient = Vector3f(0.3f)
                specular = Vector3f(0.1f)
                roughness = 0.1f
                metallic = 0.000001f
            }

            val board = TextBoard("SourceSansPro-Light.ttf")
            board.spatial().scale = Vector3f(scale)
            board.spatial().rotation.rotateX(-Math.PI.toFloat() / 2f)
            board.transparent = 0
            board.fontColor = Vector4f(0f)
            board.backgroundColor = Vector4f(0.7f)

            mesh.addChild(board)
            mesh.addChild(sphere)
            mesh.visible = true
        }
    }

    init {
        genesToLoad.visible = true
        val labelList = arrayListOf(transcription, categoryLabel)

        for (label in labelList) {
            label.transparent = 0
            label.fontColor = Vector4f(0f)
            label.backgroundColor = Vector4f(0.7f)
            label.spatial {
                scale = Vector3f(0.012f)
                rotation.rotateX(-Math.PI.toFloat() / 2f)
            }
        }
        transcription.spatial().position = Vector3f(0.03f, 0.01f, 0.02f)
        micButton.material().textures["diffuse"] =
            Texture.fromImage(Image.fromResource("volumes/mic_image.jpg", this::class.java))
        micButton.material {
            metallic = 0.3f
            roughness = 0.9f
            diffuse = Vector3f(0.5f)
        }
        micButton.spatial().position = Vector3f(0f, 0.01f, 0.02f)

        // define features of category label (display selected category on controller)
        categoryLabel.spatial {
            scale = Vector3f(scale)
            position = Vector3f(0.1f, 0f, 0f)
        }

        dispMainUI()
    }

    private fun dispMainUI() {
        val resetUIText = resetUI.children.first() as TextBoard
        val switchSelectionModeUIText = switchSelectionModeUI.children.first() as TextBoard
        val loadGenesUIText = loadGenesUI.children.first() as TextBoard

        resetUIText.text = "reset"
        switchSelectionModeUIText.text = "switch selector"
        loadGenesUIText.text = "load genes"

        resetUIText.spatial().position =
            Vector3f(-triSize + (scale / sqrt(2f)), -0.025f, triSize - triOffset - (scale / sqrt(2f)))
        (resetUI.children.last() as Icosphere).spatial().position = Vector3f(-triSize, -0.025f, triSize - triOffset)

        switchSelectionModeUIText.spatial().position =
            Vector3f(triSize + (scale / sqrt(2f)), -0.025f, triSize - triOffset - (scale / sqrt(2f)))
        (switchSelectionModeUI.children.last() as Icosphere).spatial().position =
            Vector3f(triSize, -0.025f, triSize - triOffset)

        loadGenesUIText.spatial().position =
            Vector3f((scale / sqrt(2f)), -0.025f, -triSize - triOffset - (scale / sqrt(2f)))
        (loadGenesUI.children.last() as Icosphere).spatial().position = Vector3f(0f, -0.025f, -triSize - triOffset)
    }

    /**
     * load the precomputer genes of a selected cluster as a mesh of textboards and attach it to the user's left
     * controller. Fetch the gene expression data for the pre-computer gene names
     **/
    fun dispGenes(selectedCluster: Int) {
        if (selectedCluster != -1 && !currentlyFetching) {
            currentlyFetching = true
//            val geneIndices = ArrayList<Int>()

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
            val geneIDs = clusterData.second.first
            val geneIndices = if (geneIDs.isNotEmpty()) geneIDs[0].map {
                parent.plot.annFetcher.feature_id.indexOf(it)
            } as ArrayList<Int> else arrayListOf()

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

            if (clusterData.second.first.isNotEmpty()) {
                val backC = ((selectedCluster.toFloat()) / (clusterData.first.toFloat() - 1)) * 0.99f
                clusterData.second.first[0].withIndex().forEach {
                    val geneTag = TextBoard("SourceSansPro-Light.ttf")
                    geneTag.text =
                        geneNames[it.index] +
                                ", p: " + clusterData.second.second[0][it.index].toString() +
                                ", f.c.: " + clusterData.second.third[0][it.index].toString()
                    geneTag.spatial().scale = Vector3f(scale)
                    geneTag.spatial().rotation.rotateX(-Math.PI.toFloat() / 2f)
                    geneTag.spatial().position = Vector3f(
                        0.1f,
                        0f,
                        (it.index * scale) + (scale * 1.15f)
                    )
                    geneTag.transparent = 0
                    geneTag.fontColor = Vector4f(0f)
                    geneTag.backgroundColor = parent.plot.rgbColorSpectrum.sample(backC)

                    geneTagMesh.addChild(geneTag)
                }
                (geneTagMesh.children.first() as TextBoard).fontFamily = "SourceSansPro-Semibold.ttf"

                parent.hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                    if (device.value.role == TrackerRole.LeftHand) {
                        device.value.model?.addChild(geneTagMesh)
                        device.value.model?.addChild(categoryLabel)
                    }
                }
            }
            currentlyFetching = false
        }
    }
    /**
     * addDecodedGene takes alternatives returned by LibVosk and checks if they match a known gene.
     * If a match is found, it is added to the XUi property requestedGenesIndices to be loaded on user request.
     * If not, the first alternative is loaded on a red instead of green label.
     */
    fun addDecodedGene(alternatives: List<String>) {
        // doesn't seem to pick up the first letter or do a good job of decoding.
        val geneLabel = TextBoard("SourceSansPro-Light.ttf")
        geneLabel.visible = true
        geneLabel.transparent = 0
        geneLabel.fontColor = Vector4f(0f)
        geneLabel.spatial {
            scale = Vector3f(0.012f)
            rotation.rotateX(-Math.PI.toFloat() / 2f)
        }

        val checks = alternatives.map { alt ->
            parent.plot.annFetcher.feature_name.indexOf(alt)
        } as ArrayList<Int>

        geneLabel.backgroundColor = Vector3f(1.00f, 0.73f, 0.60f).xyzw() //start red and turn green if found

        var chosenAlternative = ""
        for (result in checks.withIndex()) {
            if (result.value != -1) {
                chosenAlternative = alternatives[result.index]
                geneLabel.backgroundColor = Vector3f(0.73f, 1.00f, 0.60f).xyzw()
                requestedGenesIndices.add(result.value)
                break
            } else {
                chosenAlternative = alternatives[0]
            }
        }
        geneLabel.text = chosenAlternative
        requestedGenesNames.add(chosenAlternative)

        geneLabel.spatial().position = Vector3f(0.03f, 0.01f, 0.025f + (0.012f * (requestedGenesNames.size)))
        genesToLoad.addChild(geneLabel)
    }
}
