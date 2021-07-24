package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.xyz
import graphics.scenery.utils.extensions.xyzw
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import org.joml.Vector3f
import kotlinx.coroutines.*
import org.joml.Vector4f
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.math.sqrt

/**
 * To run at full VR HMD res, set system property -Dscenery.Renderer.ForceUndecoratedWindow=true in the
 * VM options in Run Configurations
 * @author Luke Hyman <lukejhyman@gmail.com>
 */
var currentlyFetching = false

val geneScaleMesh = Mesh()

var dotMesh = Mesh()

var textBoardMesh = Mesh()

// control annotation and gene
var genePicker = 0
var annotationPicker = 0

var annotationMode = true

//val rightLaser = Cylinder(0.01f, 2.0f, 10)
val rightSelector = Icosphere(0.2f, 5)

//val leftLaser = Cylinder(0.01f, 2.0f, 10)
val leftSelector = Icosphere(0.2f, 5)

private lateinit var cam: Camera

val encoding = "plasma"

class XVisualization constructor(val resource: Array<String> = emptyArray()) :
    SceneryBase("XVisualization", 2560, 1440, wantREPL = false) {

    private lateinit var hmd: OpenVRHMD
    lateinit var plot: XPlot

    // init here so can be accessed by input commands
    private val geneBoard = TextBoard()
    private val maxTick = TextBoard()

    val geneTagMesh = HashMap<Int, Mesh>()

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if (!hmd.initializedAndWorking()) {
            logger.info("Visualization is running without a hmd and may have limited interactivity")
        }

//        val filename = resource[0]
        plot = XPlot("tabula-muris-senis-droplet-processed-official-annotations-Marrow_vr_processed.h5ad")

        // Magic to get the VR to start up
        hmd.let { hub.add(SceneryElement.HMDInput, it) }
        settings.set("Renderer.DisableVsync", true)
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        // add parameter hmd to DetachedHeadCamera for VR
        cam = DetachedHeadCamera(hmd)
        with(cam) {
            position = Vector3f(0f, 0f, 0f)
//            position = Vector3f(0.0f, 1.65f, 5.0f)
            perspectiveCamera(70.0f, windowWidth, windowHeight, 0.1f, 1000.0f)
            this.addChild(Sphere(3f, 1)) // cam bounding box
            scene.addChild(this)
        }

        loadEnvironment()

        val testLabel = TextBoard()
        testLabel.transparent = 0
        testLabel.fontColor = Vector4f(0f)
        testLabel.backgroundColor = Vector4f(0.5f)
        testLabel.text = "switch view"
        testLabel.position = Vector3f(0.01f, 0.01f, 0.03f)
        testLabel.scale = Vector3f(0.012f)
        testLabel.rotation.rotateX(-Math.PI.toFloat() / 2f)

        thread {
            while (!running) {
                Thread.sleep(200)
            }
            hmd.events.onDeviceConnect.add { hmd, device, timestamp ->
                if (device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                    device.model?.let {
                        hmd.attachToNode(device, it, cam)
                        if (device.role == TrackerRole.RightHand) {
                            it.addChild(rightSelector)
                            it.addChild(testLabel)
                        }
                        if (device.role == TrackerRole.LeftHand) {
                            it.addChild(leftSelector)
                        }
                    }
                }
            }
        }
        thread {
            cam.update.add {
                plot.labelList.forEach { it.visible = false }
                if (annotationMode) {
                    plot.labelList[annotationPicker].children.filter { board ->
                        cam.children.first().intersects(board.children.first())
                    }
                        .forEach { board ->
                            board.visible = true
                        }
                }
            }
        }

//        thread {
//            cam.update.add {
//                for (i in 1..plot.masterMap.size) {
//                    plot.masterMap[i]?.instances?.forEach {
//                        if (cam.children.first().intersects(it)) {
//                            it.metadata["selected"] = true
//                            it.material.diffuse = Vector3f(1f, 0f, 0f)
//                            for (master in 1..plot.masterMap.size)
//                                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
//                            plot.updateInstancingLambdas()
//                            for (master in 1..plot.masterMap.size)
//                                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
//                        }
//                    }
//                }
//            }
//        }
        scene.addChild(plot)
    }

    private fun loadEnvironment() {

        val tetrahedron = arrayOfNulls<Vector3f>(4)
        tetrahedron[0] = Vector3f(1.0f, 0f, -1.0f / sqrt(2.0).toFloat())
        tetrahedron[1] = Vector3f(-1.0f, 0f, -1.0f / sqrt(2.0).toFloat())
        tetrahedron[2] = Vector3f(0.0f, 1.0f, 1.0f / sqrt(2.0).toFloat())
        tetrahedron[3] = Vector3f(0.0f, -1.0f, 1.0f / sqrt(2.0).toFloat())

        val lights = ArrayList<PointLight>()
        for (i in 0..3) {
            val light = PointLight(150.0f)
            light.position = tetrahedron[i]!!.mul(25.0f)
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 1.0f
            lights.add(light)
            scene.addChild(light)
        }

        val floor = InfinitePlane()
        floor.baseLineWidth = 1.5f
        floor.type = InfinitePlane.Type.Grid
        floor.name = "Floor"
        scene.addChild(floor)

        //text board displaying name of gene currently encoded as colormap. Disappears if color encodes cell type
        geneBoard.transparent = 1
        geneBoard.fontColor = Vector3f(1f, 1f, 1f).xyzw()
        geneBoard.position = Vector3f(-2.5f, 1.5f, -12.4f) // on far wall
        geneBoard.scale = Vector3f(1f, 1f, 1f)
        geneScaleMesh.addChild(geneBoard)

//      create y axis cylinder to add center to data exploration
        val y = generateAxis("Y", 50.00f)
        scene.addChild(y)

        // give lasers texture and set them to be visible (could use to have different lasers/colors/styles and switch between them)
        initializeSelector(rightSelector)
        initializeSelector(leftSelector)

        val colorMapScale = Box(Vector3f(5.0f, 1.0f, 0f))
        val minTick = TextBoard()

        colorMapScale.material.textures["diffuse"] =
            Texture.fromImage(Image.fromResource("volumes/colormap-$encoding.png", this::class.java))
        colorMapScale.material.metallic = 0.3f
        colorMapScale.material.roughness = 0.9f
        colorMapScale.position = Vector3f(0f, 3f, -12.4f)
        geneScaleMesh.addChild(colorMapScale)

        minTick.text = "0"
        minTick.transparent = 1
        minTick.fontColor = Vector3f(1f, 1f, 1f).xyzw()
        minTick.position = Vector3f(-2.5f, 3.5f, -12.4f)
        geneScaleMesh.addChild(minTick)

        // class object as it needs to be changed when genes toggled
        maxTick.text = maxExprList[genePicker].toString()
        maxTick.transparent = 1
        maxTick.fontColor = Vector3f(1f, 1f, 1f).xyzw()
        maxTick.position = Vector3f(2.1f, 3.5f, -12.4f)
        geneScaleMesh.addChild(maxTick)

        geneScaleMesh.visible = false
        scene.addChild(geneScaleMesh)
    }

    private fun generateAxis(dimension: String = "x", length: Float = 5.00f): Cylinder {
        val cyl: Cylinder = when (dimension.capitalize()) {
            "X" -> {
                Cylinder.betweenPoints(Vector3f(-length, 0f, 0f), Vector3f(length, 0f, 0f), radius = 0.005f)
            }
            "Y" -> {
                Cylinder.betweenPoints(Vector3f(0f, -length, 0f), Vector3f(0f, length, 0f), radius = 0.005f)
            }
            "Z" -> {
                Cylinder.betweenPoints(Vector3f(0f, 0f, -length), Vector3f(0f, 0f, length), radius = 0.005f)
            }
            else -> throw IllegalArgumentException("$dimension is not a valid dimension")
        }
        cyl.material.roughness = 0.18f
        cyl.material.metallic = 0.001f
        cyl.material.diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        return cyl
    }

    private fun initializeSelector(selectorName: Icosphere) {
        selectorName.material.diffuse = Vector3f(0.2f, 0.2f, 0.2f)
        selectorName.material.ambient = Vector3f(0.3f, 0.3f, 0.3f)
        selectorName.material.specular = Vector3f(0.1f, 0.1f, 0.1f)
        selectorName.material.roughness = 0.1f
        selectorName.material.metallic = 0.000001f
//        laserName.rotation.rotateY(-Math.PI.toFloat() / 3f) // point laser forwards
        selectorName.position = Vector3f(0f, 0.2f, -0.35f)
        selectorName.visible = true

    }

    override fun inputSetup() {
        super.inputSetup()
        // see [OpenVRhmd?.toAWTKeyCode] for key bindings

//        inputHandler?.let { handler ->
//            hashMapOf(
//                "move_forward" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Up),
//                "move_back" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Down),
//                "move_left" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Left),
//                "move_right" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Right)
//            ).forEach { (name, key) ->
//                handler.getBehaviour(name)?.let { b ->
//                    logger.info("Adding behaviour $name bound to $key to HMD")
//                    hmd.addBehaviour(name, b)
//                    hmd.addKeyBinding(name, key)
//                }
//            }
//        }

        hmd.addBehaviour("increase_size", ClickBehaviour { _, _ ->
            dotMesh.children.forEach { it ->
                it.instances.forEach {
                    it.needsUpdate = true
                    it.needsUpdateWorld = true
                }
                it.scale *= 1.02f
            }
            textBoardMesh.children.forEach {
                it.scale *= 1.02f
            }

            for (master in 1..plot.masterMap.size) {
                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("increase_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Right) //L

        hmd.addBehaviour("decrease_size", ClickBehaviour { _, _ ->
            dotMesh.children.forEach { it ->
                it.instances.forEach {
                    it.needsUpdate = true
                    it.needsUpdateWorld = true
                }
                it.scale /= 1.02f
            }
            textBoardMesh.children.forEach {
                it.scale /= 1.02f
            }
            for (master in 1..plot.masterMap.size) {
                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("decrease_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Left) //H

        inputHandler?.addBehaviour("toggle_forward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) { // freeze current annotation selection if in gene mode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = false
                    annotationPicker += 1
                    annotationPicker %= annotationList.size
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                } else if (!annotationMode && geneNames.size > 1) {
                    genePicker += 1
                    genePicker %= geneNames.size
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()
                }
                plot.updateInstancingLambdas()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        inputHandler?.addKeyBinding("toggle_forward", "L")

        hmd.addBehaviour("toggle_forward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) { // freeze current annotation selection if in gene mode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = false
                    annotationPicker += 1
                    annotationPicker %= annotationList.size
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                } else if (!annotationMode && geneNames.size > 1) {
                    genePicker += 1
                    genePicker %= geneNames.size
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()
                }
                plot.updateInstancingArrays()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("toggle_forward", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Side) //M

        inputHandler?.addBehaviour("toggle_backward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) {
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = false
                    if (annotationPicker > 0) {
                        annotationPicker -= 1
                    } else {
                        annotationPicker = annotationList.size - 1
                    }
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                } else if (!annotationMode && geneNames.size > 1) {
                    if (genePicker > 0) {
                        genePicker -= 1
                    } else {
                        genePicker = geneNames.size - 1
                    }
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()
                }
                plot.updateInstancingLambdas()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        inputHandler?.addKeyBinding("toggle_backward", "O")

        hmd.addBehaviour("toggle_backward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) {
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = false
                    if (annotationPicker > 0) {
                        annotationPicker -= 1
                    } else {
                        annotationPicker = annotationList.size - 1
                    }
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                } else if (!annotationMode && geneNames.size > 1) {
                    if (genePicker > 0) {
                        genePicker -= 1
                    } else {
                        genePicker = geneNames.size - 1
                    }
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()
                }
                plot.updateInstancingArrays()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("toggle_backward", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Side) //N

        //try openAL for audio - spatial audio - sound sources that move around - connect to a node? See link to tutorial:
        //http://wiki.lwjgl.org/wiki/OpenAL_Tutorial_1_-_Single_Static_Source.html

        hmd.addBehaviour("toggleMode", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) { // true -> annotation encoded as color
                    annotationMode = !annotationMode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList.forEach { it.visible = false }
                } else { // false -> gene expression encoded as color
                    annotationMode = !annotationMode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                }
                geneBoard.text = "Gene: " + geneNames[genePicker]
                maxTick.text = maxExprList[genePicker].toString()
                geneScaleMesh.visible = !geneScaleMesh.visible

                for (i in 1..plot.masterMap.size) {
                    plot.masterMap[i]?.instances?.forEach {
                        it.metadata["selected"] = false
                    }
                }

                plot.updateInstancingLambdas()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("toggleMode", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Menu) //X

        inputHandler?.addBehaviour("toggleMode", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
                if (annotationMode) { // true -> annotation encoded as color
                    annotationMode = !annotationMode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList.forEach { it.visible = false }
                } else { // false -> gene expression encoded as color
                    annotationMode = !annotationMode
                    if (plot.annKeyList.size > 0)
                        plot.annKeyList[annotationPicker].visible = true
                }
                geneBoard.text = "Gene: " + geneNames[genePicker]
                maxTick.text = maxExprList[genePicker].toString()

                geneScaleMesh.visible = !geneScaleMesh.visible

                plot.updateInstancingArrays()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        inputHandler?.addKeyBinding("toggleMode", "X")

        hmd.addBehaviour("markPoints", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.Default) {
//                Thread.currentThread().priority = Thread.MIN_PRIORITY

                val selectedClusters = arrayListOf<Int>()

                for (i in 1..plot.masterMap.size) {
                    plot.masterMap[i]?.instances?.forEach {
                        it.metadata["selected"] = false
                    }
                }

                for (label in plot.labelList[annotationPicker].children.withIndex()) {
                    if (label.value.children.first().intersects(rightSelector)) {
                        for (i in 1..plot.masterMap.size) {
                            plot.masterMap[i]?.instances?.forEach {
                                if (it.metadata[annotationList[annotationPicker]] == label.index.toByte() || it.metadata[annotationList[annotationPicker]] == label.index.toShort()) {
                                    it.metadata["selected"] = true
                                }
                            }
                        }
                        selectedClusters.add(label.index)
                    }
                }
                plot.updateInstancingLambdas()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()

                val clusterData =
                    ArrayList<Pair<Int, Triple<ArrayList<ArrayList<String>>, ArrayList<ArrayList<Float>>, ArrayList<ArrayList<Float>>>>>()

                if (selectedClusters.isNotEmpty()) {
                    val geneIndices = ArrayList<Int>()

                    hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                        if (device.value.role == TrackerRole.LeftHand) {
                            geneTagMesh.forEach {
                                device.value.model?.removeChild(it.value)
                            }
                        }
                    }
                    geneTagMesh.clear()

                    val scale = 0.02f

                    for (cluster in selectedClusters.withIndex()) {

                        val clusterMesh = Mesh()
                        clusterData.add(plot.annFetcher.precompGenesReader(annotationPicker, cluster.value))

                        if (clusterData[cluster.index].second.first.isNotEmpty()) {
                            val backC =
                                ((cluster.value.toFloat()) / (clusterData[cluster.index].first.toFloat() - 1)) * 0.99f

                            for (i in 0 until clusterData[cluster.index].second.first[0].size) {

                                geneIndices.add(plot.annFetcher.geneNames.indexOf(clusterData[cluster.index].second.first[0][i]))

                                val geneTag = TextBoard()
                                geneTag.text =
                                    clusterData[cluster.index].second.first[0][i] +
                                            ", p: " + clusterData[cluster.index].second.second[0][i].toString() +
                                            ", f.c.: " + clusterData[cluster.index].second.third[0][i].toString()

                                geneTag.scale = Vector3f(scale)
                                geneTag.position = Vector3f(
                                    0.05f,
                                    0f,
                                    (i * scale) + (cluster.index * scale * clusterData[cluster.index].second.first[0].size)
                                )
                                geneTag.rotation.rotateX(-Math.PI.toFloat() / 2f)
                                geneTag.transparent = 0
                                geneTag.fontColor = Vector4f(0f)
                                geneTag.backgroundColor = plot.rgbColorSpectrum.sample(backC)
                                geneTag.name = i.toString()

                                clusterMesh.addChild(geneTag)
                            }
                        }
                        geneTagMesh[cluster.value] = clusterMesh
                    }
                    hmd.getTrackedDevices(TrackedDeviceType.Controller).forEach { device ->
                        if (device.value.role == TrackerRole.LeftHand) {
                            geneTagMesh.forEach {
                                device.value.model?.addChild(it.value)
                            }
                        }
                    }
                    ////////////////////////////////////////


                    val buffer = plot.annFetcher.fetchGeneExpression(geneIndices)
                    genePicker = 0
                    geneNames.clear()
                    geneExpr.clear()
                    geneNames = buffer.first
                    geneExpr = buffer.second
                    maxExprList = buffer.third

                    plot.updateInstancingArrays()
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()

                    ///////////////////////////////////////
                }
            }
        })
        hmd.addKeyBinding("markPoints", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Trigger) //U

        hmd.addBehaviour("unmarkPoints", ClickBehaviour { _, _ ->
            for (i in 1..plot.masterMap.size) {
                plot.masterMap[i]?.instances?.forEach {
                    if (leftSelector.intersects(it)) {
                        it.metadata["selected_testing"] = true
                        it.material.diffuse = Vector3f(0.73f, 1.00f, 0.60f)
                    }
                }
            }
            plot.updateInstancingLambdas()
            for (master in 1..plot.masterMap.size)
                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        })
        hmd.addKeyBinding("unmarkPoints", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Trigger)

        hmd.addBehaviour("extendSelector", ClickBehaviour { _, _ ->
            if (rightSelector.scale[0] <= 1.7f) {
                rightSelector.scale *= 1.10f
                leftSelector.scale = rightSelector.scale
            }
        })
        hmd.addKeyBinding("extendSelector", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Up) //K

        hmd.addBehaviour("shrinkSelector", ClickBehaviour { _, _ ->
            if (rightSelector.scale[0] >= 0.05f) {
                rightSelector.scale /= 1.1f
                leftSelector.scale = rightSelector.scale
            }
        })
        hmd.addKeyBinding("shrinkSelector", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Down) //J

        //        inputHandler?.addBehaviour("fetchCurrentSelection", ClickBehaviour { _, _ ->
        hmd.addBehaviour("fetchCurrentSelection", ClickBehaviour { _, _ ->
            if (!currentlyFetching) {
                thread {
                    currentlyFetching = true
//                Thread.currentThread().priority = Thread.MIN_PRIORITY
                    geneBoard.text = "fetching..."

                    val selectedList = ArrayList<Int>()
                    val backgroundList = ArrayList<Int>()

                    for (i in 1..plot.masterMap.size) {
                        plot.masterMap[i]?.instances?.forEach {
                            if (it.metadata["selected_testing"] == true) {
                                selectedList.add(it.metadata["index"] as Int)
                            } else {
                                backgroundList.add(it.metadata["index"] as Int)
                            }
                        }
                    }

                    println(selectedList.size)
                    println(backgroundList.size)
                    when {
                        selectedList.size == 0 || backgroundList.size == 0 -> {
                        }
                        else -> {
                            val buffer = plot.annFetcher.fetchGeneExpression(
                                plot.maxDiffExpressedGenes(
                                    selectedList,
                                    backgroundList,
                                    "TTest"
                                )
                            )
                            genePicker = 0
                            geneNames.clear()
                            geneExpr.clear()
                            geneNames = buffer.first
                            geneExpr = buffer.second
                            maxExprList = buffer.third
                        }
                    }

                    for (i in 1..plot.masterMap.size) {
                        plot.masterMap[i]?.instances?.forEach {
                            it.metadata["selected_testing"] = false
                        }
                    }

                    plot.updateInstancingArrays()
                    geneBoard.text = "Gene: " + geneNames[genePicker]
                    maxTick.text = maxExprList[genePicker].toString()
                    currentlyFetching = false
                }
            }
        })
        hmd.addKeyBinding("fetchCurrentSelection", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Menu)
//        inputHandler?.addKeyBinding("fetchCurrentSelection", "G")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("scenery.Renderer.Device", "3070")
            System.setProperty("scenery.Renderer", "VulkanRenderer")
            System.setProperty("scenery.Renderer.ForceUndecoratedWindow", "true")
            XVisualization().main()
            if (args.isNotEmpty()) {
                println(args[0])
            }
        }
    }
}
