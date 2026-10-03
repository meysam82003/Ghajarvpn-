package net.gozar.app

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

class SubscriptionTlsTest {
 @Test fun selfSignedSubscriptionIsRejectedInsteadOfSilentlyTrustingIt()=runBlocking {
  // Test-only private key, never packaged in the application or used to sign it.
  val keys=KeyStore.getInstance("PKCS12")
  javaClass.getResourceAsStream("/untrusted-localhost.p12")!!.use {keys.load(it,"test-fixture-only".toCharArray())}
  val managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(keys,"test-fixture-only".toCharArray())}
  val tls=SSLContext.getInstance("TLS").apply {init(managers.keyManagers,null,null)}
  val server=HttpsServer.create(InetSocketAddress("127.0.0.1",0),0)
  server.httpsConfigurator=HttpsConfigurator(tls)
  server.createContext("/") { exchange ->
   val body="socks://192.0.2.1:1080#synthetic".toByteArray()
   exchange.sendResponseHeaders(200,body.size.toLong());exchange.responseBody.use {it.write(body)}
  }
  server.start()
  try {
   val error=runCatching {SubscriptionFetcher.fetchFull("https://127.0.0.1:${server.address.port}/")}.exceptionOrNull()
   assertTrue("Untrusted TLS must fail before subscription parsing",error is SSLHandshakeException)
  } finally {server.stop(0)}
 }
}
