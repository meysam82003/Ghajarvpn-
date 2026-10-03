package net.gozar.app.security.vault

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/** Verification happens on the written bytes, before atomic replace; no destructive copy fallback. */
object VaultFileStore {
    fun read(file: File): ByteArray = file.inputStream().use { net.gozar.app.configtoolkit.BoundedInput.read(it,VaultFormat.MAX_FILE.toLong()) }
    @Synchronized fun save(file: File, bytes: ByteArray, verify: (ByteArray)->Unit) {
        require(bytes.size <= VaultFormat.MAX_FILE)
        val dir=requireNotNull(file.absoluteFile.parentFile); require(dir.isDirectory || dir.mkdirs())
        val temp=Files.createTempFile(dir.toPath(),"gsb-",".pending").toFile()
        try {
            Files.setPosixFilePermissions(temp.toPath(),PosixFilePermissions.fromString("rw-------"))
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            verify(read(temp))
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}
