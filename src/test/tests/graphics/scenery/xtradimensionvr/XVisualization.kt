package graphics.scenery.xtradimensionvr

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.extensions.times
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import java.io.File
import kotlin.concurrent.thread
import org.joml.Vector3f

/**
 * To run at full VR HMD res, set system property -Dscenery.Renderer.ForceUndecoratedWindow=true in the
 * VM options in Run Configurations
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */
class XVisualization: SceneryBase("XVisualization", 2560, 1440) {
    //2560 1440
    private lateinit var hmd: OpenVRHMD
    lateinit var plot: XPlot

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if(!hmd.initializedAndWorking()) {
            logger.info("Visualization is running without a hmd and may have limited interactivity")
        }

        // pick data set (file). No default functionality atm
        val defaultData = ""
        val defaultFile = File(defaultData)
        val filename = if (defaultFile.exists()){
            defaultData
        } else {
            val c = Context()
            val ui = c.getService(UIService::class.java)
            val file = ui.chooseFile(null, FileWidget.OPEN_STYLE)
            file.absolutePath
        }

        plot = XPlot(filename)

        // Magic to get the VR to start up
        hmd.let { hub.add(SceneryElement.HMDInput, it) }
        settings.set("Renderer.DisableVsync", true)
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        renderer?.toggleVR()

        // add parameter hmd to DetachedHeadCamera for VR
        val cam: Camera = DetachedHeadCamera(hmd)
        with(cam) {
            position = Vector3f(0.0f, 0.0f, 3.5f)
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            scene.addChild(this)
        }
        cam.addChild(plot.geneBoard)


        thread {
            while(!running) {
                Thread.sleep(200)
            }
            hmd.events.onDeviceConnect.add { hmd, device, timestamp ->
                if(device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                     device.model?.let { hmd.attachToNode(device, it, cam)
                        if(device.role == TrackerRole.RightHand) {
                            it.addChild(plot.laser)
                        }
                        if(device.role == TrackerRole.LeftHand) {
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
                    "move_right" to OpenVRHMD.keyBinding(TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Right)).forEach { (name, key) ->
                handler.getBehaviour(name)?.let { b ->
                    logger.info("Adding behaviour $name bound to $key to HMD")
                    hmd.addBehaviour(name, b)
                    hmd.addKeyBinding(name, key)
                }
            }
        }

        hmd.addBehaviour("increase_size", ClickBehaviour{ _, _->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.v.scale = plot.v.scale * 1.02f
            plot.textBoardMesh.scale = plot.textBoardMesh.scale * 1.02f
        })
        hmd.addKeyBinding("increase_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Right) //L


        hmd.addBehaviour("decrease_size", ClickBehaviour{ _, _ ->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.v.scale = plot.v.scale * (1.0f/1.02f)
            plot.textBoardMesh.scale = plot.textBoardMesh.scale * (1.0f/1.02f)
        })
        hmd.addKeyBinding("decrease_size", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Left) //H


        hmd.addBehaviour("toggle_genes_forwards", ClickBehaviour{ _, _ ->
            if(plot.genePicker < plot.geneNames.size - 1){
                plot.genePicker += 1
            }
            else{
                plot.genePicker = 0
            }
            plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
            if(plot.textBoardPicker){
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
            }
        })
        hmd.addKeyBinding("toggle_genes_forwards", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Menu) //M


        hmd.addBehaviour("toggle_genes_backwards", ClickBehaviour { _, _ ->
            if(plot.genePicker > 0){
                plot.genePicker -= 1
            }
            else{
                plot.genePicker = plot.geneNames.size - 1
            }
            plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
            if(plot.textBoardPicker){
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
            }
        })
        hmd.addKeyBinding("toggle_genes_backwards", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Menu) //N

        //try openAL for audio - spatial audio - sound sources that move around - connect to a node? See link to tutorial:
        //http://wiki.lwjgl.org/wiki/OpenAL_Tutorial_1_-_Single_Static_Source.html

        //adding a pointing laser for interacting with objects
      hmd.addBehaviour("toggle_laser", object: ClickBehaviour {
           val timeout = 500*1000*1000
           var last = 0L

            override fun click(p0: Int, p1: Int) {
                logger.info("Toggling laser")
                if(System.nanoTime() - last < timeout) return
                plot.laser.visible = !plot.laser.visible
                plot.laser2.visible = !plot.laser2.visible
                last = System.nanoTime()
            }
        })
        hmd.addKeyBinding("toggle_laser", "Y")

        /*
        toggles between cell name and gene name view by triggering if statement in instancing zip
        in cell name view: makes cell name boards invisible and activates gene name label in fov
        in gene name view: makes cell name boards visible (even if made invisible by toggle_genes behaviour)
        note from Ulrik: "when textBoards are shown transparent, the rendering order matters"
         */
        hmd.addBehaviour("toggleTextBoards", ClickBehaviour {_, _ ->
            if(plot.textBoardPicker && plot.textBoardMesh.visible){
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
                plot.textBoardPicker = !plot.textBoardPicker
            } else if(plot.textBoardPicker && !plot.textBoardMesh.visible){
                plot.textBoardPicker = !plot.textBoardPicker
            } else if(!plot.textBoardPicker && !plot.textBoardMesh.visible){
                plot.textBoardPicker = !plot.textBoardPicker
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
            }
            plot.geneBoard.visible = !plot.geneBoard.visible
            plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
        })
        hmd.addKeyBinding("toggleTextBoards", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Side) //X

        inputHandler?.addBehaviour("toggleTextBoards", ClickBehaviour {_, _ ->
            if(plot.textBoardPicker && plot.textBoardMesh.visible){
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
                plot.textBoardPicker = !plot.textBoardPicker
            } else if(plot.textBoardPicker && !plot.textBoardMesh.visible){
                plot.textBoardPicker = !plot.textBoardPicker
            } else if(!plot.textBoardPicker && !plot.textBoardMesh.visible){
                plot.textBoardPicker = !plot.textBoardPicker
                plot.textBoardMesh.visible = !plot.textBoardMesh.visible
            }
            plot.geneBoard.visible = !plot.geneBoard.visible
            plot.geneBoard.text = "Gene: " + plot.geneNames[plot.genePicker]
        })

        inputHandler?.addKeyBinding("toggleTextBoards", "X")


        hmd.addBehaviour("toggleDataSets", ClickBehaviour { _, _ ->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.currentDatasetIndex = (plot.currentDatasetIndex + 1) % plot.dataSet.size
        })
        hmd.addKeyBinding("toggleDataSets", TrackerRole.RightHand, OpenVRHMD.OpenVRButton.Side) //Y
        // currently has no function as datasetIndex is not used

        hmd.addBehaviour("deletePoints", ClickBehaviour { _, _ ->
            plot.v.instances.forEach {
                if(plot.laser2.intersects(it)){
                    //if(plot.laser2.intersects(it, parent = plot.v)){
                    it.visible = false
                }
            }
        })
        hmd.addKeyBinding("deletePoints", TrackerRole.LeftHand, OpenVRHMD.OpenVRButton.Trigger) // T

        hmd.addBehaviour("markPoints", ClickBehaviour { _, _ ->
            plot.v.instances.forEach {
                if(plot.laser.intersects(it)){
                    //if(plot.laser.intersects(it, parent = plot.v)){
                    it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f)
                    //it.material.diffuse = Vector3f(1.0f, 0.0f, 0.0f, 1.0f)
                    it.metadata["selected"] = true//!(it.metadata["selected"] as? Boolean ?: false)
                }
            }
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

        inputHandler?.addBehaviour("reloadFile", ClickBehaviour { _, _ -> plot.reload()  })
        inputHandler?.addKeyBinding("reloadFilea -Xmx8ge", "shift R")

    }

    @Test
    override fun main() {
        super.main()
    }
}
