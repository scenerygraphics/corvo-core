package graphics.scenery.xtradimensionvr

import org.scijava.ui.behaviour.DragBehaviour
import java.io.File
import java.io.IOException
import javax.sound.sampled.*
import javax.sound.sampled.AudioFileFormat
import kotlin.concurrent.thread


class PressAndRelease(private val parent: XVisualization) : DragBehaviour {

    // the line from which audio data is captured
    private lateinit var line: TargetDataLine

    // path of the wav file
    private val wavFile = File("test_rec.wav")

    // format of audio file
    private val fileType: AudioFileFormat.Type = AudioFileFormat.Type.WAVE

    override fun init(x: Int, y: Int) {
        thread {
            try {
                val format = getAudioFormat()
                val info = DataLine.Info(TargetDataLine::class.java, format)

                // checks if system supports the data line
                if (!AudioSystem.isLineSupported(info)) {
                    println("Line not supported")
                    System.exit(0)
                }
                line = AudioSystem.getLine(info) as TargetDataLine
                line.open(format)
                line.start() // start capturing
//                println("Start capturing...")
                val ais = AudioInputStream(line)
//                println("Start recording...")

                // start recording
                AudioSystem.write(ais, fileType, wavFile)
            } catch (ex: LineUnavailableException) {
                ex.printStackTrace()
            } catch (ioe: IOException) {
                ioe.printStackTrace()
            }
        }
    }

    override fun drag(x: Int, y: Int) {}

    override fun end(x: Int, y: Int) {
        line.stop()
        line.close()
//        parent.speechGene.text = "transcribing..."
        thread {
            val geneIndexList = ArrayList<Int>()
            parent.audioDecoder.decodeAudio("test_rec.wav").forEach {
                geneIndexList.add(parent.plot.annFetcher.geneNames.indexOf(it))
            }

            // add each element as a text board, colored according to whether it was found

        }
    }

    private fun getAudioFormat(): AudioFormat {
        val sampleRate = 16000f
        val sampleSizeInBits = 16
        val channels = 1
        val signed = true
        val bigEndian = true
        return AudioFormat(
            sampleRate, sampleSizeInBits,
            channels, signed, bigEndian
        )
    }
}