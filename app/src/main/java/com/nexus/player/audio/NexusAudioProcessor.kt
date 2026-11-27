package com.nexus.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Media3 adapter: PCM16/float ByteBuffer ⇄ [DspEngine]. Passthrough audio never reaches this. */
@UnstableApi
class NexusAudioProcessor(private val engine: DspEngine) : BaseAudioProcessor() {
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        engine.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val ch = inputAudioFormat.channelCount
        val pcm16 = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val n = if (pcm16) bytes / 2 else bytes / 4
        if (scratch.size < n) scratch = FloatArray(n)
        if (pcm16) for (i in 0 until n) scratch[i] = inputBuffer.getShort() / 32768f
        else for (i in 0 until n) scratch[i] = inputBuffer.getFloat()
        engine.process(scratch, n / ch, ch)
        val out = replaceOutputBuffer(bytes)
        if (pcm16) for (i in 0 until n) out.putShort((scratch[i] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
        else for (i in 0 until n) out.putFloat(scratch[i])
        out.flip()
    }

    override fun onFlush() = engine.reset()
}
