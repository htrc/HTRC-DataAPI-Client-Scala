package org.hathitrust.htrc.tools.dataapi.utils

import java.security.KeyStore
import java.security.cert.X509Certificate

import javax.net.ssl._

object CryptoUtils {

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  lazy val insecureSocketFactory: SSLSocketFactory = {
    val noopTrustManager = Array[TrustManager](new X509TrustManager() {
      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
      override def getAcceptedIssuers: Array[X509Certificate] = null
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, noopTrustManager, null)
    sslContext.getSocketFactory
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def getFactory(keyStore: KeyStore, secret: String, sslValidation: Boolean = true): SSLSocketFactory = {
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, secret.toCharArray)

    val trustManagers =
      if (sslValidation) null
      else Array[TrustManager](new X509TrustManager() {
        override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
        override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {}
        override def getAcceptedIssuers: Array[X509Certificate] = null
      })

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagers, null)

    context.getSocketFactory
  }

}