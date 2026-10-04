package net.gozar.app
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class UpdateFilesTest {
 @Test fun completeDownloadIsPromotedAndPartialIsRemoved()=runBlocking {
  val d=Files.createTempDirectory("update-test").toFile()
  try {
   assertTrue(runCatching { UpdateFiles.receive(d,byteArrayOf(1).inputStream(),2){_,_->} }.isFailure)
   assertEquals(0,d.listFiles()!!.size)
   val f=UpdateFiles.receive(d,byteArrayOf(1,2).inputStream(),2){_,_->}
   assertArrayEquals(byteArrayOf(1,2),f.readBytes());assertEquals("apk",f.extension)
   UpdateFiles.recover(d);assertEquals(0,d.listFiles()!!.size)
  } finally { d.deleteRecursively() }
 }
 @Test fun cancelledDownloadLeavesNoPartialOrApk()=runBlocking {
  val d=Files.createTempDirectory("update-cancel").toFile()
  try {
   val job=launch { UpdateFiles.receive(d,ByteArray(131072).inputStream(),131072){_,_->cancel()} }
   job.join();assertTrue(job.isCancelled);assertEquals(0,d.listFiles()!!.size)
  } finally { d.deleteRecursively() }
 }
}
