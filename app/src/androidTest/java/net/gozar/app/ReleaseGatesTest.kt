package net.gozar.app

import android.app.NotificationManager
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReleaseGatesTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 @Test fun updaterRejectsCorruptWrongPackageWrongDigestAndWrongSigner() {
  val own=File(context.applicationInfo.sourceDir)
  val sha=MessageDigest.getInstance("SHA-256").digest(own.readBytes()).joinToString(""){"%02x".format(it)}
  assertEquals(GhajarUpdateInstaller.VerifyResult.Ok,GhajarUpdateInstaller.verifySha256(own,sha))
  assertTrue(GhajarUpdateInstaller.verifySha256(own,"0".repeat(64)) is GhajarUpdateInstaller.VerifyResult.ChecksumMismatch)
  assertTrue(GhajarUpdateInstaller.verifySha256(own,null) is GhajarUpdateInstaller.VerifyResult.Unavailable)
  assertEquals(GhajarUpdateInstaller.VerifyResult.Ok,GhajarUpdateInstaller.verifySignatureMatchesInstalled(context,own))
  val wrongPackage=File(instrumentation.context.applicationInfo.sourceDir)
  assertTrue(GhajarUpdateInstaller.verifySignatureMatchesInstalled(context,wrongPackage) is GhajarUpdateInstaller.VerifyResult.SignatureMismatch)
  val broken=File(context.cacheDir,"corrupt-test.apk")
  try { broken.writeBytes(byteArrayOf(1,2,3));assertTrue(GhajarUpdateInstaller.verifySignatureMatchesInstalled(context,broken) is GhajarUpdateInstaller.VerifyResult.Unavailable) } finally { broken.delete() }
  val differentSigner=File(context.getExternalFilesDir(null),"wrong-signer.apk")
  assertTrue("CI must supply a real re-signed APK fixture",differentSigner.isFile)
  assertTrue(GhajarUpdateInstaller.verifySignatureMatchesInstalled(context,differentSigner) is GhajarUpdateInstaller.VerifyResult.SignatureMismatch)
 }
 @Test fun quickControlsDefaultToggleAndUnknownLocation() {
  val prefs=context.getSharedPreferences(GhajarQuickControls.CHANNEL,0);val old=GhajarQuickControls.enabled(context)
  try {
   prefs.edit().remove("enabled").commit();assertTrue(GhajarQuickControls.enabled(context))
   GhajarQuickControls.refresh(context)
   val manager=context.getSystemService(NotificationManager::class.java)
   val notification=manager.activeNotifications.single { it.notification.channelId==GhajarQuickControls.CHANNEL }.notification
   instrumentation.runOnMainSync {
    val view=notification.bigContentView.apply(context,null)
    assertTrue(view.findViewById<TextView>(R.id.quick_location).text.contains("نامشخص"))
    assertEquals(ghajarPaletteFor(context).primary.toArgb(),view.findViewById<TextView>(R.id.quick_open).currentTextColor)
    assertTrue(view.findViewById<android.view.View>(R.id.quick_open).hasOnClickListeners())
    assertTrue(view.findViewById<android.view.View>(R.id.quick_toggle).hasOnClickListeners())
    assertTrue(view.findViewById<android.view.View>(R.id.quick_next).hasOnClickListeners())
   }
   GhajarQuickControls.setEnabled(context,false)
   assertFalse(manager.activeNotifications.any { it.notification.channelId==GhajarQuickControls.CHANNEL })
  } finally { GhajarQuickControls.setEnabled(context,old) }
 }
 @Test fun bothWidgetsUsePersistedPaletteAndBindActions() {
  val original=GhajarLookStore.load(context).value
  try {
   for(look in listOf(original.copy(accent=0xff126abc),original.copy(amoled=true,accent=0xffc01234))) {
    GhajarLookStore.set(context,look)
    instrumentation.runOnMainSync {
     val p=ghajarPaletteFor(context)
     val big=GhajarWidget.build(context).apply(context,null)
     val small=GhajarWidgetSmall.build(context).apply(context,null)
     assertEquals(p.surface.toArgb(),(big.findViewById<android.view.View>(R.id.widget_root).background as ColorDrawable).color)
     assertEquals(p.surface.toArgb(),(small.findViewById<android.view.View>(R.id.widget_small_root).background as ColorDrawable).color)
     assertEquals(p.primary.toArgb(),big.findViewById<TextView>(R.id.widget_next_btn).currentTextColor)
     assertTrue(small.findViewById<android.view.View>(R.id.widget_small_button).hasOnClickListeners())
     for(id in intArrayOf(R.id.widget_toggle,R.id.widget_ping_btn,R.id.widget_subs_btn,R.id.widget_next_btn))assertTrue(big.findViewById<android.view.View>(id).hasOnClickListeners())
    }
   }
  } finally { GhajarLookStore.set(context,original) }
 }
}
