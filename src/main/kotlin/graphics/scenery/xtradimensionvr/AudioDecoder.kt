package graphics.scenery.xtradimensionvr

import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.IOException
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException


class AudioDecoder(val parent: XVisualization) {

    private val model = Model("model")
    private val rc = Recognizer(model, 16000f)

    private val phonesToNum = hashMapOf(
        "zero" to 0,
        "one" to 1,
        "two" to 2,
        "to" to 2,
        "three" to 3,
        "four" to 4,
        "for" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "ate" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "fourteen" to 14,
        "fifteen" to 15,
        "sixteen" to 16,
        "seventeen" to 17,
        "eighteen" to 18,
        "nineteen" to 1,
        "twenty" to 20,
        "twenty one" to 21,
        "twenty two" to 22,
        "twenty three" to 23,
        "twenty four" to 24,
        "twenty five" to 25,
        "twenty six" to 26,
        "twenty seven" to 27,
        "twenty eight" to 28,
        "twenty nine" to 29,
        "thirty" to 30
    )

    init {
        LibVosk.setLogLevel(LogLevel.INFO)
    }

    @Throws(IOException::class, UnsupportedAudioFileException::class)
    fun decodeAudio(file: String): ArrayList<String> {
        val utteranceList = arrayListOf<MutableList<String>>()
        val joinedUtteranceList = arrayListOf<String>()

        AudioSystem.getAudioInputStream(BufferedInputStream(FileInputStream(file))).use { ais ->
            rc.also { recognizer ->
                var nbytes: Int
                val b = ByteArray(4096)
                while (ais.read(b).also { nbytes = it } >= 0) {
                    if (recognizer.acceptWaveForm(b, nbytes)) {
                        utteranceList.add(recognizer.result.toString().drop(14).dropLast(3).split(" ").toMutableList())
                    }
                }
                utteranceList.add(recognizer.finalResult.toString().drop(14).dropLast(3).split(" ").toMutableList())
            }

            for (utterance in utteranceList) {
                for (word in utterance.withIndex()) {
                    if (phonesToNum.containsKey(word.value)) {
                        utterance[word.index] = phonesToNum[word.value].toString()
                    }
                }
                utterance[0] = utterance[0].toUpperCase()
                joinedUtteranceList.add(utterance.joinToString(""))
            }
        }

        return joinedUtteranceList
    }
}