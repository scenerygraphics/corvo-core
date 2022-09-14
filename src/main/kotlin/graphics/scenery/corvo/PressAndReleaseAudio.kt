package graphics.scenery.corvo

import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour
import java.io.File
import java.io.IOException
import javax.sound.sampled.*
import javax.sound.sampled.AudioFileFormat
import kotlin.concurrent.thread


class PressAndReleaseAudio(private val parent: XVisualization) : DragBehaviour {

    var noRescale = false

    override fun init(x: Int, y: Int) {
        thread {
            // don't launch decoder if not gracefully shut down
            if (!parent.audioDecoder.inProgressFlag) {
                parent.ui.micButton.position = Vector3f(0f, 0.018f, 0.02f)
                parent.ui.micButton.scale.x *= 1.3f
                parent.ui.micButton.scale.z *= 1.3f

                noRescale = false

                parent.audioDecoder.liveFlag = true
                parent.audioDecoder.decodeLiveVosk()
            }
            else {
                noRescale = true  // don't give visual signal that recording is in progress to avoid confusion
            }
        }
    }

    override fun drag(x: Int, y: Int) {}

    override fun end(x: Int, y: Int) {
        parent.audioDecoder.liveFlag = false

        if (!noRescale) {
            parent.ui.micButton.position = Vector3f(0f, 0.01f, 0.02f)
            parent.ui.micButton.scale.x /= 1.3f
            parent.ui.micButton.scale.z /= 1.3f
            noRescale = false
        }
    }
}
