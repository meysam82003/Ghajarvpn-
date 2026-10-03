package net.gozar.app

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext

/** Private random temporary files; only a complete, fsynced download is promoted. */
internal object UpdateFiles {
    suspend fun receive(directory: File, input: InputStream, size: Long, progress: (Long,Long)->Unit): File {
        require(size in 1..512L*1024*1024)
        require(directory.isDirectory || directory.mkdirs())
        val temporary=File.createTempFile("update-", ".part", directory)
        temporary.setReadable(false,false);temporary.setWritable(false,false)
        require(temporary.setReadable(true,true) && temporary.setWritable(true,true))
        val completed=File(directory,temporary.name.removeSuffix(".part")+".apk")
        try {
            temporary.outputStream().use { output ->
                val buffer=ByteArray(65536);var total=0L
                while(true) {
                    coroutineContext.ensureActive()
                    val count=input.read(buffer);if(count<0)break
                    require(count.toLong()<=size-total) { "Download size mismatch" }
                    output.write(buffer,0,count);total+=count;progress(total,size)
                }
                require(total==size) { "Download size mismatch" }
                output.fd.sync()
            }
            coroutineContext.ensureActive()
            Files.move(temporary.toPath(),completed.toPath(),StandardCopyOption.ATOMIC_MOVE)
            return completed
        } catch(e:Throwable) { completed.delete();throw e }
        finally { temporary.delete() }
    }
    fun recover(directory: File) { directory.listFiles()?.filter { it.name.startsWith("update-") && it.extension in setOf("apk","part") }?.forEach { it.delete() } }
}
