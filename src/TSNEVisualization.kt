package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import java.io.File
import java.io.IOException
import javax.sound.sampled.*
import kotlin.concurrent.thread

/**
 * To run at full VR HMD res, set system property -Dscenery.Renderer.ForceUndecoratedWindow=true in the
 * VM options in Run Configurations
 *
 * @author Luke Hyman <lukejhyman@gmail.com>
 */
class TSNEVisualization: SceneryBase("TSNEVisualization", 2560, 1440) {
    //2560 1440
    var hmd: OpenVRHMD? = OpenVRHMD(useCompositor = true)
    lateinit var plot: TSNEPlot
    private lateinit var globalCam: Camera

    override fun init() {
        val dataset = System.getProperty("tsneDataset")
        val filename = if(dataset == null) {
            val c = Context()
            val ui = c.getService(UIService::class.java)
            val file = ui.chooseFile(null, FileWidget.OPEN_STYLE)
            file.absolutePath
        } else {
            dataset
        }

        plot = TSNEPlot(filename)

        // Magic to get the VR to start up
        hmd?.let { hub.add(SceneryElement.HMDInput, it) }
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
//        renderer?.toggleVR()

        // add parameter hmd for VR
        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true
            scene.addChild(this)
        }
        globalCam = cam

        thread {
            while(!running) {
                Thread.sleep(200)
            }
            hmd?.events?.onDeviceConnect?.add { hmd, device, timestamp ->
                if(device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                    device.model?.let { hmd?.attachToNode(device, it, cam)
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

    class AudioPlayer : LineListener {
        var playCompleted: Boolean = false

        fun play(audioFilePath: String) {
            val audioFile = File(audioFilePath)

            try {
                val audioStream = AudioSystem.getAudioInputStream(audioFile)
                val format = audioStream.format
                val info = DataLine.Info(Clip::class.java, format)
                val audioClip = AudioSystem.getLine(info) as Clip
                audioClip.addLineListener(this)
                audioClip.open(audioStream)
                audioClip.start()

                while (!playCompleted) {
                    // wait for the playback completes
                    try {
                        Thread.sleep(1000)
                    } catch (ex: InterruptedException) {
                        ex.printStackTrace()
                    }
                }
                audioClip.close()

            } catch (ex: UnsupportedAudioFileException) {
                println("The specified audio file is not supported.")
                ex.printStackTrace()
            } catch (ex: LineUnavailableException) {
                println("Audio line for playing back is unavailable.")
                ex.printStackTrace()
            } catch (ex: IOException) {
                println("Error playing the audio file.")
                ex.printStackTrace()
            }
        }

        override fun update(event: LineEvent) {
            val type = event.type
            if (type == LineEvent.Type.START) {
                println("Playback started.")
            } else if (type == LineEvent.Type.STOP) {
                playCompleted = true
                println("Playback completed.")
            }
        }
    }//audio class

    override fun inputSetup() {
        super.inputSetup()
        // see [OpenVRhmd?.toAWTKeyCode] for key bindings

        inputHandler?.let { handler ->
            hashMapOf(
                    "move_forward" to "W",
                    "move_back" to "S",
                    "move_left" to "A",
                    "move_right" to "D").forEach { (name, key) ->
                handler.getBehaviour(name)?.let { b ->
                    hmd?.addBehaviour(name, b)
                    hmd?.addKeyBinding(name, key)
                }
            }
        }

        val increaseSize = ClickBehaviour { _, _ ->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.v.scale = plot.v.scale * 1.02f
            plot.textBoardMesh.scale = plot.textBoardMesh.scale * 1.02f
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("increase_size", increaseSize)
        hmd?.addKeyBinding("increase_size", "L")

        val decreaseSize = ClickBehaviour { _, _ ->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.v.scale = plot.v.scale * (1.0f/1.02f)
            plot.textBoardMesh.scale = plot.textBoardMesh.scale * (1.0f/1.02f)
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("decrease_size", decreaseSize)
        hmd?.addKeyBinding("decrease_size", "H")

        val toggleGenesForwards= ClickBehaviour { _, _ ->
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
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("toggle_genes_forwards", toggleGenesForwards)
        hmd?.addKeyBinding("toggle_genes_forwards", "I")

        val toggleGenesBackwards= ClickBehaviour { _, _ ->
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
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("toggle_genes_backwards", toggleGenesBackwards)
        hmd?.addKeyBinding("toggle_genes_backwards", "O")

        //try openAL for audio - spatial audio - sound sources that move around - connect to a node? See link to tutorial:
        //http://wiki.lwjgl.org/wiki/OpenAL_Tutorial_1_-_Single_Static_Source.html

        //adding a pointing laser for interacting with objects
//    hmd?.addBehaviour("toggle_laser", object: ClickBehaviour {
//        val timeout = 500*1000*1000
//        var last = 0L
//
//        override fun click(p0: Int, p1: Int) {
//            logger.info("Toggling laser")
//            if(System.nanoTime() - last < timeout) return
//            plot.laser.visible = !plot.laser.visible
//            plot.laser2.visible = !plot.laser2.visible
//            last = System.nanoTime()
//        }
//    })
//    hmd?.addKeyBinding("toggle_laser", "Y")

        val toggleTextBoards = ClickBehaviour {_, _ ->
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
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("toggleTextBoards", toggleTextBoards)
        hmd?.addKeyBinding("toggleTextBoards", "X")

        val toggleDataSets = ClickBehaviour { _, _ ->
            plot.dotMesh.children.firstOrNull()?.instances?.forEach{
                it.needsUpdate = true
                it.needsUpdateWorld = true
            }
            plot.currentDatasetIndex = (plot.currentDatasetIndex + 1) % plot.dataSet.size

        }

        hmd?.addBehaviour("toggleDataSets", toggleDataSets)
        hmd?.addKeyBinding("toggleDataSets", "Y")

        val audioFeedback = ClickBehaviour { _, _ ->
            val audioFilePath = "audioFiles/LRMonoPhase4.wav"
            val player = AudioPlayer()
            player.play(audioFilePath)
        }

        inputHandler?.addBehaviour("audioFeedback", audioFeedback)
        inputHandler?.addKeyBinding("audioFeedback", "B")

        val deletePoints = ClickBehaviour { _, _ ->
            plot.v.instances.forEach {
                if(plot.laser2.intersects(it, parent = plot.v)){
                    it.visible = false
                }
            }
            plot.v.notifyUpdate()
        }

        val markPoints = ClickBehaviour { _, _ ->
            plot.v.instances.forEach {
                if(plot.laser.intersects(it, parent = plot.v)){
                    it.material.diffuse = GLVector(1.0f, 0.0f, 0.0f, 1.0f)
                    it.metadata["selected"] = true//!(it.metadata["selected"] as? Boolean ?: false)
                }
            }
            plot.v.notifyUpdate()
        }

        hmd?.addBehaviour("deletePoints", deletePoints)
        hmd?.addKeyBinding("deletePoints", "T")

        hmd?.addBehaviour("markPoints", markPoints)
        hmd?.addKeyBinding("markPoints", "U")

        val extendLaser = ClickBehaviour{ _, _ ->
            val scale = plot.laser.scale
            scale.set(1, scale.y() * 1.1f)
            plot.laser.scale = scale
            plot.laser2.scale = scale
        }

        val shrinkLaser = ClickBehaviour { _, _ ->
            val scale = plot.laser.scale
            scale.set(1, scale.y() / 1.1f)
            plot.laser.scale = scale
            plot.laser2.scale = scale
        }

        hmd?.addBehaviour("extendLaser", extendLaser)
        hmd?.addKeyBinding("extendLaser", "K")

        hmd?.addBehaviour("shrinkLaser", shrinkLaser)
        hmd?.addKeyBinding("shrinkLaser", "J")

        inputHandler?.addBehaviour("resetVisibility", ClickBehaviour { _, _ -> plot.resetVisibility() })
        inputHandler?.addKeyBinding("resetVisibility", "R")

        inputHandler?.addBehaviour("reloadFile", ClickBehaviour { _, _ -> plot.reload()  })
        inputHandler?.addKeyBinding("reloadFile", "shift R")

    }

    @Test
    override fun main() {
        super.main()
    }
}


//C:\Program Files\Oculus\Support\oculus-diagnostics\OculusMirror.exe --Size 2560 1440 --FovTanAngleMultiplier 1.3 1.3 --DisableTimewarp --SymmetricFov --RectilinearBothEyes --DisableFovStencil --IncludeSystemGui --IncludeNotifications
