package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.times
import graphics.scenery.utils.extensions.xyzw
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import org.joml.Vector3f
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.math.sqrt

/**
 * To run at full VR HMD res, set system property -Dscenery.Renderer.ForceUndecoratedWindow=true in the
 * VM options in Run Configurations
 * intersection and generating an integer list of selected cells
 * generate integer list of cells expressing chosen gene
 * implement hypergeometric test
 * @author Luke Hyman <lukejhyman@gmail.com>
 */
val geneScaleMesh = Mesh()

var dotMesh = Mesh()

var textBoardMesh = Mesh()

// control annotation and gene
var genePicker = 0
var annotationPicker = 0

var annotationMode = true

val rightLaser = Cylinder(0.01f, 2.0f, 10)
val leftLaser = Cylinder(0.01f, 2.0f, 10)

private lateinit var cam: Camera

val encoding = "plasma"

class XVisualization constructor(val resource: Array<String> = emptyArray()) :
    SceneryBase("XVisualization", 2560, 1440, wantREPL = false) {

    private lateinit var hmd: OpenVRHMD
    lateinit var plot: XPlot

    // init here so can be accessed by input commands
    val geneBoard = TextBoard()
    val maxTick = TextBoard()

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if (!hmd.initializedAndWorking()) {
            logger.info("Visualization is running without a hmd and may have limited interactivity")
        }

//        val filename = resource[0]
        plot = XPlot("marrow_vr_processed.h5ad")

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
//            this.addChild(Sphere(1f, 1)) // cam bounding box
            scene.addChild(this)
        }

        loadEnvironment()

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
                            it.addChild(rightLaser)
                        }
                        if (device.role == TrackerRole.LeftHand) {
                            it.addChild(leftLaser)
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

        // Make a headlight for the camera
        val headlight = PointLight(2.0f)
        headlight.position = Vector3f(0f, 0f, -1f).mul(25.0f)
        headlight.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        headlight.intensity = 0.5f
        headlight.name = "headlight"

        val lightSphere = Icosphere(1.0f, 2)
        headlight.addChild(lightSphere)
        lightSphere.material.diffuse = headlight.emissionColor
        lightSphere.material.specular = headlight.emissionColor
        lightSphere.material.ambient = headlight.emissionColor
        lightSphere.material.wireframe = true
        lightSphere.visible = false
        cam.addChild(headlight)

        val floor = InfinitePlane()
        floor.baseLineWidth = 1.5f
        floor.type = InfinitePlane.Type.Grid
        (floor).name = "Floor"
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
        initializeLaser(rightLaser)
        initializeLaser(leftLaser)

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

    private fun initializeLaser(laserName: Cylinder) {
        laserName.material.diffuse = Vector3f(0.9f, 0.0f, 0.0f)
        laserName.material.metallic = 0.001f
        laserName.material.roughness = 0.18f
        laserName.rotation.rotateX(-Math.PI.toFloat() / 1.5f) // point laser forwards
        laserName.visible = true
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
                    annotationPicker %= plot.annotationList.size
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
                    annotationPicker %= plot.annotationList.size
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
                        annotationPicker = plot.annotationList.size - 1
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
                        annotationPicker = plot.annotationList.size - 1
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

                plot.updateInstancingLambdas()
                for (master in 1..plot.masterMap.size)
                    (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
            }
        })
        hmd.addKeyBinding("toggleMode",  TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Menu) //X

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
            for (i in 1..plot.masterMap.size) {
                plot.masterMap[i]?.instances?.forEach {
                    if (rightLaser.intersects(it)) {
                        it.metadata["selected"] = true
                        it.material.diffuse = Vector3f(1f, 0f, 0f)
                        for (master in 1..plot.masterMap.size)
                            (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
                    }
                }
            }
            plot.updateInstancingLambdas()
            for (master in 1..plot.masterMap.size)
                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        })
        hmd.addKeyBinding("markPoints", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Trigger) //U

        hmd.addBehaviour("unmarkPoints", ClickBehaviour { _, _ ->
            for (i in 1..plot.masterMap.size) {
                plot.masterMap[i]?.instances?.forEach {
                    if (leftLaser.intersects(it)) {
                        it.metadata["selected"] = false
                    }
                }
            }
            plot.updateInstancingLambdas()
            for (master in 1..plot.masterMap.size)
                (plot.masterMap[master]?.metadata?.get("MaxInstanceUpdateCount") as AtomicInteger).getAndIncrement()
        })
        hmd.addKeyBinding("unmarkPoints", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Trigger)

        hmd.addBehaviour("extendLaser", ClickBehaviour { _, _ ->
            val scale = rightLaser.scale
            scale.y *= 1.10f
            rightLaser.scale = scale
            leftLaser.scale = scale
        })
        hmd.addKeyBinding("extendLaser", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Up) //K

        hmd.addBehaviour("shrinkLaser", ClickBehaviour { _, _ ->
            val scale = rightLaser.scale
            scale.y /= 1.1f
            rightLaser.scale = scale
            leftLaser.scale = scale
        })
        hmd.addKeyBinding("shrinkLaser", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Down) //J

        hmd.addBehaviour("fetchCurrentSelection", ClickBehaviour { _, _ ->
            thread {
//                Thread.currentThread().priority = Thread.MIN_PRIORITY
                geneBoard.text = "fetching..."

                val selectedList = ArrayList<Int>()
                val backgroundList = ArrayList<Int>()

                for (i in 1..plot.masterMap.size) {
                    plot.masterMap[i]?.instances?.forEach {
                        if (it.metadata["selected"] == true) {
                            selectedList.add(it.metadata["index"] as Int)
                        } else {
                            backgroundList.add(it.metadata["index"] as Int)
                        }
                    }
                }
                when {
                    selectedList.size == 0 || backgroundList.size == 0 -> {
//                        geneBoard.text = "no differential selection made, fetching random genes..."
                        val buffer = plot.annFetcher.fetchGeneExpression()
                        genePicker = 0
                        geneNames.clear()
                        geneExpr.clear()
                        geneNames = buffer.first
                        geneExpr = buffer.second
                        maxExprList = buffer.third
                    }
                    else -> {
                        val buffer = plot.annFetcher.fetchGeneExpression(
                            plot.maxDiffExpressedGenes(
                                selectedList,
                                backgroundList
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
                        it.metadata["selected"] = false
                    }
                }

                plot.updateInstancingArrays()
                geneBoard.text = "Gene: " + geneNames[genePicker]
                maxTick.text = maxExprList[genePicker].toString()
            }
        })
        hmd.addKeyBinding("fetchCurrentSelection", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Menu)
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
