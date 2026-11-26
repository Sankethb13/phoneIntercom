package com.intercom.ride.audio

import com.intercom.ride.net.CodecId

/** Turns 20 ms PCM frames into network packets. */
interface AudioEncoder {
    val codecId: Byte
    fun encode(pcm: ShortArray): List<ByteArray>
    fun release()
}

/** Turns network packets back into 16 kHz mono PCM. */
interface AudioDecoder {
    fun decode(payload: ByteArray): ShortArray
    fun release()
}

class MuLawEncoder : AudioEncoder {
    override val codecId = CodecId.MULAW
    override fun encode(pcm: ShortArray) = listOf(MuLawCodec.encode(pcm))
    override fun release() = Unit
}

class MuLawDecoder : AudioDecoder {
    override fun decode(payload: ByteArray) = MuLawCodec.decode(payload)
    override fun release() = Unit
}
