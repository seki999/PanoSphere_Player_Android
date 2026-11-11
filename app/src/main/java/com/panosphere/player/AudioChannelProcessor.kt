@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.panosphere.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

enum class AudioChannel(val label: String) {
    STEREO("立体声"),
    LEFT("左声道"),
    RIGHT("右声道")
}

internal class AudioChannelProcessor : BaseAudioProcessor() {
    @Volatile var channel = AudioChannel.STEREO

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return if (inputAudioFormat.channelCount >= 2) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 can pass its shared empty buffer when draining the processor chain.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val selected = channel
        if (selected == AudioChannel.STEREO) {
            output.put(inputBuffer)
        } else {
            val channels = inputAudioFormat.channelCount
            while (inputBuffer.hasRemaining()) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                val sample = if (selected == AudioChannel.LEFT) left else right
                output.putShort(sample)
                output.putShort(sample)
                repeat(channels - 2) {
                    inputBuffer.short
                    output.putShort(0)
                }
            }
        }
        output.flip()
    }
}
