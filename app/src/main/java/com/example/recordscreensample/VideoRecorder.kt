package com.example.recordscreensample
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer

class VideoRecorder {

    private lateinit var mediaMuxer: MediaMuxer
    private var videoTrackIndex = -1
    private var isMuxerStarted = false

    fun startRecording(outputPath: String) {
        try {
            mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun addVideoTrack(mediaFormat: MediaFormat) {
        videoTrackIndex = mediaMuxer.addTrack(mediaFormat)
        isMuxerStarted = true
        mediaMuxer.start()
    }

    fun writeSampleData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Log.e("VideoRecorder", "Muxer not started.")
            return
        }

        mediaMuxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
    }

    fun stopRecording() {
        if (!isMuxerStarted) {
            Log.e("VideoRecorder", "Muxer not started.")
            return
        }

        try {
            mediaMuxer.stop()
            mediaMuxer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
