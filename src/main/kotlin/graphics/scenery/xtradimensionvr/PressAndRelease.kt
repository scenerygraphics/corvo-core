package graphics.scenery.xtradimensionvr

import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import java.io.File
import java.io.IOException
import javax.sound.sampled.*
import javax.sound.sampled.AudioFileFormat
import kotlin.concurrent.thread


class PressAndRelease(private val parent: XVisualization) : DragBehaviour {

    var noRescale = false

    override fun init(x: Int, y: Int) {

        thread {
            if (!parent.audioDecoder.inProgressFlag) {
                parent.micButton.position = Vector3f(0f, 0.018f, 0.02f)
                parent.micButton.scale.x *= 1.3f
                parent.micButton.scale.z *= 1.3f
                parent.audioDecoder.liveFlag = true
                parent.audioDecoder.decodeLiveVosk()
            }
            else {
                // decoding still in progress, please wait and try again
                noRescale = true
            }
        }

    }

    override fun drag(x: Int, y: Int) {}

    override fun end(x: Int, y: Int) {
        parent.audioDecoder.liveFlag = false

        if (!noRescale) {
            parent.micButton.position = Vector3f(0f, 0.01f, 0.02f)
            parent.micButton.scale.x /= 1.3f
            parent.micButton.scale.z /= 1.3f
            noRescale = false
        }

    }

}