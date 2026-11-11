package com.panosphere.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChannelProcessorTest {
    @Test
    fun switchesBetweenLeftRightAndStereoWithoutReconfiguration() {
        val processor = AudioChannelProcessor()
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        processor.queueInput(AudioProcessor.EMPTY_BUFFER)
        assertEquals(0, processor.output.remaining())

        fun checkFrame(channel: AudioChannel, expectedLeft: Short, expectedRight: Short) {
            processor.channel = channel
            val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            input.putShort(100).putShort(-200).flip()
            processor.queueInput(input)
            val output = processor.output
            assertEquals(4, output.remaining())
            assertEquals(expectedLeft, output.short)
            assertEquals(expectedRight, output.short)
        }

        checkFrame(AudioChannel.LEFT, 100, 100)
        checkFrame(AudioChannel.RIGHT, -200, -200)
        checkFrame(AudioChannel.STEREO, 100, -200)
    }

    @Test
    fun monoDoesNotRequireChannelProcessing() {
        val processor = AudioChannelProcessor()
        val format = AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(format))
    }
}
