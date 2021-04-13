package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.times
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import org.joml.Vector3f
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * To run at full VR HMD res, set system property -Dscenery.Renderer.ForceUndecoratedWindow=true in the
 * VM options in Run Configurations
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */
class XVisualization constructor(val resource: Array<String> = emptyArray()) :
    SceneryBase("XVisualization", 2560, 1440, wantREPL = false) {

    private lateinit var hmd: OpenVRHMD
    lateinit var plot: XPlot
    var previousAnnotation = 0

    private val lock = Mutex()

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if (!hmd.initializedAndWorking()) {
            logger.info("Visualization is running without a hmd and may have limited interactivity")
        }

//        val filename = resource[0]

        plot = XPlot()

        // Magic to get the VR to start up
        hmd.let { hub.add(SceneryElement.HMDInput, it) }
        settings.set("Renderer.DisableVsync", true)
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        // add parameter hmd to DetachedHeadCamera for VR
        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 3.5f)
            perspectiveCamera(60.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }
//        cam.addChild(plot.geneBoard)

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
                            it.addChild(plot.laser)
                        }
                        if (device.role == TrackerRole.LeftHand) {
                            it.addChild(plot.laser2)
                        }
                    }
                }
            }
        }
        scene.addChild(plot)

    }

    override fun inputSetup() {
        super.inputSetup()
        // see [OpenVRhmd?.toAWTKeyCode] for key bindings

        inputHandler?.let { handler ->
            hashMapOf(
                "move_forward" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Up),
                "move_back" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Down),
                "move_left" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Left),
                "move_right" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Right)
            ).forEach { (name, key) ->
                handler.getBehaviour(name)?.let { b ->
                    logger.info("Adding behaviour $name bound to $key to HMD")
                    hmd.addBehaviour(name, b)
                    hmd.addKeyBinding(name, key)
                }
            }
        }

        hmd.addBehaviour("increase_size", ClickBehaviour { _, _ ->
            plot.dotMesh.children.forEach { it ->
                it.instances.forEach {
                    it.needsUpdate = true
                    it.needsUpdateWorld = true
                }
                it.scale *= 1.02f
            }
        })
        hmd.addKeyBinding("increase_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Right) //L

        hmd.addBehaviour("decrease_size", ClickBehaviour { _, _ ->
            plot.dotMesh.children.forEach { it ->
                it.instances.forEach {
                    it.needsUpdate = true
                    it.needsUpdateWorld = true
                }
                it.scale /= 1.02f
            }
        })
        hmd.addKeyBinding("decrease_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Left) //H

        hmd.addBehaviour("toggle_genes_forwards", ClickBehaviour { _, _ ->
            if (!plot.annotationMode) {
                plot.genePicker += 1
                plot.genePicker %= plot.geneNames.size

                plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
                if (plot.annotationMode) {
                    plot.textBoardMesh.visible = !plot.textBoardMesh.visible
                }
            }
            thread {
                plot.updateInstancingColor()
            }
        })
        hmd.addKeyBinding("toggle_genes_forwards", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Menu) //M

        inputHandler?.addBehaviour("toggle_genes_forward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.IO) {
                lock.withLock {
                    if (!plot.annotationMode) {
                        plot.genePicker += 1
                        plot.genePicker %= plot.geneNames.size
                        plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
                    }
                    plot.updateInstancingColor()
                }
            }
        })
        inputHandler?.addKeyBinding("toggle_genes_forward", "M")

        inputHandler?.addBehaviour("toggle_annotations_forward", ClickBehaviour { _, _ ->
            if (plot.annotationMode) { // freeze current annotation selection if in gene mode
                previousAnnotation = plot.annotationPicker
                plot.annotationPicker += 1
                plot.annotationPicker %= plot.annotationList.size

                plot.annKeyMap[previousAnnotation].visible = false
                plot.annKeyMap[plot.annotationPicker].visible = true
            }
            GlobalScope.launch(Dispatchers.Default) {
                plot.updateInstancingColor()
            }
        })
        inputHandler?.addKeyBinding("toggle_annotations_forward", "L")

        hmd.addBehaviour("toggle_genes_backward", ClickBehaviour { _, _ ->
            GlobalScope.launch(Dispatchers.IO) {
                lock.withLock {
                    if (plot.genePicker > 0) {
                        plot.genePicker -= 1
                    } else {
                        plot.genePicker = plot.geneNames.size - 1
                    }
                    plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
                    plot.updateInstancingColor()
                }
            }
        })
        hmd.addKeyBinding("toggle_genes_backward", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Menu) //N

        inputHandler?.addBehaviour("toggle_annotations_backward", ClickBehaviour { _, _ ->
            previousAnnotation = plot.annotationPicker
            if (plot.annotationPicker > 0) {
                plot.annotationPicker -= 1
            } else {
                plot.annotationPicker = plot.annotationList.size - 1
            }
            plot.annKeyMap[previousAnnotation].visible = false
            plot.annKeyMap[plot.annotationPicker].visible = true
            GlobalScope.launch(Dispatchers.Default) {
                plot.updateInstancingColor()
            }
        })
        inputHandler?.addKeyBinding("toggle_annotations_backward", "O")

        //try openAL for audio - spatial audio - sound sources that move around - connect to a node? See link to tutorial:
        //http://wiki.lwjgl.org/wiki/OpenAL_Tutorial_1_-_Single_Static_Source.html

        //adding a pointing laser for interacting with objects
        hmd.addBehaviour("toggle_laser", object : ClickBehaviour {
            val timeout = 500 * 1000 * 1000
            var last = 0L

            override fun click(p0: Int, p1: Int) {
                logger.info("Toggling laser")
                if (System.nanoTime() - last < timeout) return
                plot.laser.visible = !plot.laser.visible
                plot.laser2.visible = !plot.laser2.visible
                last = System.nanoTime()
            }
        })
        hmd.addKeyBinding("toggle_laser", "Y")

        hmd.addBehaviour("toggleMode", ClickBehaviour { _, _ ->
            if (plot.annotationMode && plot.textBoardMesh.visible) {
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
                plot.annotationMode = !plot.annotationMode
            } else if (plot.annotationMode && !plot.textBoardMesh.visible) {
                plot.annotationMode = !plot.annotationMode
            } else if (!plot.annotationMode && !plot.textBoardMesh.visible) {
                plot.annotationMode = !plot.annotationMode
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
            }
            plot.geneBoard.visible = !plot.geneBoard.visible
            plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
        })
        hmd.addKeyBinding("toggleMode", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Side) //X

        inputHandler?.addBehaviour("toggleMode", ClickBehaviour { _, _ ->

            GlobalScope.launch(Dispatchers.IO) {
                lock.withLock {
                    if (plot.annotationMode) { // true -> annotation encoded as color
                        plot.annotationMode = !plot.annotationMode
                        plot.annKeyMap.forEach { it.visible = false }
                    } else { // false -> gene expression encoded as color
                        plot.annotationMode = !plot.annotationMode
                        plot.annKeyMap[plot.annotationPicker].visible = true
                    }
                    plot.updateInstancingColor()
                    plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
                    plot.geneScaleMesh.visible = !plot.geneScaleMesh.visible
                }
            }
        })

        inputHandler?.addKeyBinding("toggleMode", "X")

        hmd.addBehaviour("deletePoints", ClickBehaviour { _, _ ->
            for (i in 0..plot.masterMap.size) {
                plot.masterMap[i]?.instances?.forEach {
                    if (plot.laser2.intersects(it)) {
                        //if(plot.laser2.intersects(it, parent = plot.v)){
                        it.visible = false
                    }
                }
            }

//            plot.v.instances.forEach {
//                if(plot.laser2.intersects(it)){
//                    //if(plot.laser2.intersects(it, parent = plot.v)){
//                    it.visible = false
//                }
//            }
        })
        hmd.addKeyBinding("deletePoints", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Trigger) // T

        hmd.addBehaviour("markPoints", ClickBehaviour { _, _ ->
            for (i in 0..plot.masterMap.size) {
                plot.masterMap[i]?.instances?.forEach {
                    if (plot.laser.intersects(it)) {
                        //if(plot.laser.intersects(it, parent = plot.v)){
                        it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f)
                        //it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f, 1.0f)
                        it.metadata["selected"] = true//!(it.metadata["selected"] as? Boolean ?: false)
                    }
                }
            }

//            plot.v.instances.forEach {
//                if(plot.laser.intersects(it)){
//                    //if(plot.laser.intersects(it, parent = plot.v)){
//                    it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f)
//                    //it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f, 1.0f)
//                    it.metadata["selected"] = true//!(it.metadata["selected"] as? Boolean ?: false)
//                }
//            }
        })
        hmd.addKeyBinding("markPoints", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Trigger) //U

        hmd.addBehaviour("extendLaser", ClickBehaviour { _, _ ->
            val scale = plot.laser.scale
            //scale.set(1, scale.y() * 1.1f)
            scale.y *= 1.10f
            plot.laser.scale = scale
            plot.laser2.scale = scale
        })
        hmd.addKeyBinding("extendLaser", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Up) //K

        hmd.addBehaviour("shrinkLaser", ClickBehaviour { _, _ ->
            val scale = plot.laser.scale
            //scale.set(1, scale.y() / 1.1f)
            scale.y /= 1.1f
            plot.laser.scale = scale
            plot.laser2.scale = scale
        })
        hmd.addKeyBinding("shrinkLaser", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Down) //J


        inputHandler?.addBehaviour("resetVisibility", ClickBehaviour { _, _ -> plot.resetVisibility() })
        inputHandler?.addKeyBinding("resetVisibility", "R")

        inputHandler?.addBehaviour("reloadFile", ClickBehaviour { _, _ ->
//            GlobalScope.launch(Dispatchers.IO) {
//                lock.withLock {
//                    plot.reloadCo()
//                }
//            }
            plot.reloadCo()
        })
        inputHandler?.addKeyBinding("reloadFile", "shift R")

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("scenery.Renderer.Device", "3070")
            System.setProperty("scenery.Renderer", "VulkanRenderer")
            XVisualization().main()
            if (args.isNotEmpty()) {
                println(args[0])
            }
        }
    }
}
