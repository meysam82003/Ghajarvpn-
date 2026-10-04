package net.gozar.app.configtoolkit

import java.io.InputStream
import java.io.ByteArrayOutputStream

object BoundedInput {
    fun read(input: InputStream, limit: Long = DecoderRegistry.MAX_FILE_BYTES): ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(32768)
        while(true){val n=input.read(buffer);if(n<0)break
            if(out.size().toLong()+n>limit)throw ConfigToolkitException.TooLarge(limit)
            out.write(buffer,0,n)
        };return out.toByteArray()
    }
}
