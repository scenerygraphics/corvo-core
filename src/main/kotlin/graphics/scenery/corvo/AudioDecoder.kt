package graphics.scenery.corvo

import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import javax.sound.sampled.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONString
import org.json.JSONStringer


class AudioDecoder(private val parent: XVisualization) {

    private val g = "[\"a\", \"b\", \"c\", \"d\"]"

    private val model = Model("vosk-model-small-en-us-0.15")
    private val rc = Recognizer(model, 32000f)
    private val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000f, 16, 2, 4, 44100f, false)
    private val info = DataLine.Info(TargetDataLine::class.java, format)

    var liveFlag = false
    var decodingFlag = false
    var inProgressFlag = false

    private val phonesToNum = hashMapOf(
        "zero" to 0,
        "one" to 1,
        "two" to 2,
        "to" to 2,
        "too" to 2,
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
        "nineteen" to 19,
        "twenty" to 20,
//        "twenty one" to 21,
//        "twenty two" to 22,
//        "twenty three" to 23,
//        "twenty four" to 24,
//        "twenty five" to 25,
//        "twenty six" to 26,
//        "twenty seven" to 27,
//        "twenty eight" to 28,
//        "twenty nine" to 29,
//        "thirty" to 30
    )
    private val phonesToSymbols = hashMapOf(
        "dash" to "-",
        "and" to "n"
    // issue - some gene names are capitalized after the dash!!!!!
    )

    init {
        LibVosk.setLogLevel(LogLevel.DEBUG)
        rc.setMaxAlternatives(3)
    }

    @Throws(IOException::class, UnsupportedAudioFileException::class)
    fun decodeWAVAudioVosk(file: String): ArrayList<String> {
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
                    if (phonesToSymbols.containsKey(word.value)) {
                        utterance[word.index] = phonesToSymbols[word.value].toString()
                    }
                }
                utterance[0] = utterance[0].toUpperCase()
                joinedUtteranceList.add(utterance.joinToString(""))
            }
        }

        return joinedUtteranceList
    }

    @Throws(IOException::class, UnsupportedAudioFileException::class)
    fun decodeLiveVosk() {
        inProgressFlag = true
        var microphone: TargetDataLine
        rc.also { recognizer ->
            try {
                microphone = AudioSystem.getLine(info) as TargetDataLine
                microphone.open(format)
                microphone.start()

                val out = ByteArrayOutputStream()
                var numBytesRead: Int
                val CHUNK_SIZE = 1024
                val b = ByteArray(4096)

                while (liveFlag || decodingFlag) {
                    numBytesRead = microphone.read(b, 0, CHUNK_SIZE)
                    out.write(b, 0, numBytesRead)
                    if (recognizer.acceptWaveForm(b, numBytesRead)) {

//                        println(recognizer.result.drop(21).dropLast(1))

                        val j = JSONArray(recognizer.result.drop(21).dropLast(1))
                        println(j.query("/0/text"))
                        println(j.query("/1/text"))
                        // can  now fetch n best results. Check all combinations of the word

                        val utterance = recognizer.result.toString().drop(14).dropLast(3).split(" ").toMutableList()

                        for (word in utterance.withIndex()) {
                            if (phonesToNum.containsKey(word.value)) {
                                utterance[word.index] = phonesToNum[word.value].toString()
                            }
                            if (phonesToSymbols.containsKey(word.value)) {
                                utterance[word.index] = phonesToSymbols[word.value].toString()
                            }
                        }
                        utterance[0] = utterance[0].capitalize()

//                         add to list of requested genes in Xui class
                        parent.ui.transcription.text = utterance.joinToString(" ")
                        parent.ui.addDecodedGene(utterance.joinToString(""))

                        // sometimes doesn't stream in partial results, but loads the final result successfully
                        // load custom selected cells genes using load genes sphere
                        // make reset last priority

                        decodingFlag = false

                    } else {
                        if (recognizer.partialResult[18].toString().isNotBlank()) {
                            decodingFlag = true
                            parent.ui.transcription.text = recognizer.partialResult.drop(17).dropLast(3)
                        }

                    }
                }
                microphone.close()
                inProgressFlag = false

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}