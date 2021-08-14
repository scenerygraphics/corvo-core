package graphics.scenery.xtradimensionvr

import graphics.scenery.BoundingGrid
import graphics.scenery.Box
import graphics.scenery.Mesh
import graphics.scenery.TextBoard
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import kotlin.collections.ArrayList

class Xui(parent: XVisualization) {
    private val par = parent

    var geneTagMesh = Mesh()
    var categoryLabel = TextBoard("SourceSansPro-Light.ttf")

//    val resetMesh = Mesh()
    val resetUI = TextBoard("SourceSansPro-Light.ttf")
//    val loadGenesUI = TextBoard("SourceSansPro-Light.ttf")

//    val switchSelectionMesh = Mesh()
    val switchSelectionModeUI = TextBoard("SourceSansPro-Light.ttf")
    var switchSelectionModeUIState = 0

    val scale = 0.02f

    init {

        // define features of category label (display selected category on controller)
        categoryLabel.scale = Vector3f(scale)
        categoryLabel.position = Vector3f(
            0.1f,
            0f,
            0f
        )
        categoryLabel.rotation.rotateX(-Math.PI.toFloat() / 2f)
        categoryLabel.transparent = 0
        categoryLabel.fontColor = Vector4f(0f)
        categoryLabel.backgroundColor = Vector4f(0.7f)

        dispMainUI()
    }

    fun dispMainUI() {
        val buttonArray = arrayListOf(resetUI, switchSelectionModeUI)
        resetUI.text = "reset"
//        loadGenesUI.text = "load genes"
        switchSelectionModeUI.text = "switch selector"

        for (button in buttonArray.withIndex()) {
            button.value.scale = Vector3f(scale)
            button.value.position = Vector3f(
                (-button.index * 0.18f) + 0.05f,
                0f,
                0f
            )
            button.value.rotation.rotateX(-Math.PI.toFloat() / 2f)
            button.value.transparent = 0
            button.value.fontColor = Vector4f(0f)
            button.value.backgroundColor = Vector4f(0.7f)

//            val bg = BoundingGrid()
//            bg.node = button.value
        }

    }

    fun dispGenes(selectedCluster: Int) {
        if (selectedCluster != -1) {
            val geneIndices = ArrayList<Int>()

            par.hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                if (device.value.role == TrackerRole.LeftHand) {
                    device.value.model?.removeChild(geneTagMesh)
                    device.value.model?.removeChild(categoryLabel)
                }
            }
            geneTagMesh.children.forEach {
                geneTagMesh.removeChild(it)
            }
            categoryLabel.text = par.plot.annFetcher.categoryNames[annotationPicker][selectedCluster]

            val clusterData = par.plot.annFetcher.precompGenesReader(annotationPicker, selectedCluster)

            if (clusterData.second.first.isNotEmpty()) {
                val backC =
                    ((selectedCluster.toFloat()) / (clusterData.first.toFloat() - 1)) * 0.99f

                for (i in 0 until clusterData.second.first[0].size) {

                    geneIndices.add(par.plot.annFetcher.geneNames.indexOf(clusterData.second.first[0][i]))

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
                    geneTag.backgroundColor = par.plot.rgbColorSpectrum.sample(backC)

                    geneTagMesh.addChild(geneTag)
                }
            }

            (geneTagMesh.children.first() as TextBoard).fontFamily = "SourceSansPro-Semibold.ttf"

            par.hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                if (device.value.role == TrackerRole.LeftHand) {
                    device.value.model?.addChild(geneTagMesh)
                    device.value.model?.addChild(categoryLabel)
                }
            }
            ////////////////////////////////////////

            val buffer = par.plot.annFetcher.fetchGeneExpression(geneIndices)
            genePicker = 0
            geneNames.clear()
            geneExpr.clear()
            geneNames = buffer.first
            geneExpr = buffer.second
            maxExprList = buffer.third

            par.plot.updateInstancingArrays()
            par.geneBoard.text = "Gene: " + geneNames[genePicker]
            par.maxTick.text = maxExprList[genePicker].toString()

            ///////////////////////////////////////
        }
    }

}