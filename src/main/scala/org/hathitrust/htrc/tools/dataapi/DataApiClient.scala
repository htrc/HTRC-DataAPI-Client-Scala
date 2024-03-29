package org.hathitrust.htrc.tools.dataapi

import java.net.{HttpURLConnection, URL, URLEncoder}
import java.nio.file._
import java.security.KeyStore
import java.util.zip.ZipInputStream

import build.BuildInfo
import javax.net.ssl._
import org.hathitrust.htrc.tools.dataapi.DataApiClient.Builder.Options.DefaultOptions
import org.hathitrust.htrc.tools.dataapi.exceptions.{ApiRequestException, UnsupportedProtocolException}
import org.hathitrust.htrc.tools.dataapi.utils.CryptoUtils._
import org.hathitrust.htrc.tools.dataapi.utils.FileUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.compat.IterableOnce
import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}
import scala.util.{Try, Using}

object DataApiClient {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  object Builder {
    sealed trait Options

    object Options {
      sealed trait DefaultOptions extends Options
      sealed trait UrlOption extends Options
      type RequiredOptions = DefaultOptions with UrlOption
    }

    def apply(): Builder[DefaultOptions] = new Builder
  }

  class Builder[Options <: Builder.Options](url: Option[String] = None,
                                            token: Option[String] = None,
                                            connectTimeout: Int = 0,
                                            readTimeout: Int = 0,
                                            performSSLValidation: Boolean = true,
                                            clientCertKeyStore: Option[KeyStore] = None,
                                            clientCertKeyStorePwd: Option[String] = None,
                                            followRedirects: Boolean = true,
                                            useTempStorage: Option[String] = None,
                                            failOnError: Boolean = false) {
    import Builder.Options._

    def setApiUrl(url: String): Builder[Options with UrlOption] = {
      new Builder(
        url = Some(url),
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )
    }

    def setAuthToken(token: String): Builder[Options] = {
      new Builder(
        url = url,
        token = Option(token),
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )
    }

    def setConnectTimeout(timeout: Int): Builder[Options] = {
      new Builder(
        url = url,
        token = token,
        connectTimeout = timeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )
    }

    def setReadTimeout(timeout: Int): Builder[Options] = {
      new Builder(
        url = url,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = timeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )
    }

    def disableSSLValidation(): Builder[Options] =
      new Builder(
        url = url,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = false,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )

    def useClientCertKeyStore(keyStore: KeyStore, keyStorePwd: String): Builder[Options] =
      new Builder(
        url = url,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = false,
        clientCertKeyStore = Some(keyStore),
        clientCertKeyStorePwd = Some(keyStorePwd),
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )

    def disableFollowRedirects(): Builder[Options] =
      new Builder(
        url = url,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = false,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )

    def setUseTempStorage(tmpDir: String = FileUtils.OSTmpDir, failOnError: Boolean = true): Builder[Options] =
      new Builder(
        url = url,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = Some(tmpDir),
        failOnError = failOnError
      )

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    def build()(implicit ev: Options =:= RequiredOptions): DataApi =
      new DataApiClient(
        baseUrl = url.get,
        token = token,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        performSSLValidation = performSSLValidation,
        clientCertKeyStore = clientCertKeyStore,
        clientCertKeyStorePwd = clientCertKeyStorePwd,
        followRedirects = followRedirects,
        useTempStorage = useTempStorage,
        failOnError = failOnError
      )
  }

  def apply(): Builder[DefaultOptions] = new Builder
}

sealed class DataApiClient(baseUrl: String,
                           token: Option[String],
                           connectTimeout: Int,
                           readTimeout: Int,
                           performSSLValidation: Boolean,
                           clientCertKeyStore: Option[KeyStore],
                           clientCertKeyStorePwd: Option[String],
                           followRedirects: Boolean,
                           useTempStorage: Option[String],
                           failOnError: Boolean) extends DataApi {
  import DataApiClient._

  require(baseUrl != null && baseUrl.startsWith("http"), s"Invalid URL: $baseUrl")
  require(readTimeout >= 0, s"Invalid `readTimeout` value: $readTimeout")
  require(connectTimeout >= 0, s"Invalid `connectTimeout` value: $connectTimeout")

  private val apiUrl = new URL(s"$baseUrl/".replaceAll("//$", "/"))

  if (!performSSLValidation)
    logger.warn("Disabling SSL validation! This is HIGHLY insecure and should NEVER be used in production!")

  @SuppressWarnings(Array(
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.TryPartial"
  ))
  override def retrieveVolumes(ids: IterableOnce[String])
                              (implicit codec: Codec, executionContext: ExecutionContext): Future[VolumeIterator] = Future {
    val url = new URL(apiUrl, "volumes")

    val conn = url.openConnection() match {
      case https: HttpsURLConnection =>
        (clientCertKeyStore, clientCertKeyStorePwd) match {
          case (Some(keyStore), Some(pwd)) =>
            https.setSSLSocketFactory(getFactory(keyStore, pwd, performSSLValidation))

          case _ =>
            if (!performSSLValidation) {
              https.setSSLSocketFactory(insecureSocketFactory)
            }
        }

        https
      case http: HttpURLConnection => http
      case other => throw UnsupportedProtocolException(other.getClass.getName)
    }
    conn.setConnectTimeout(connectTimeout)
    conn.setReadTimeout(readTimeout)
    conn.setInstanceFollowRedirects(followRedirects)
    conn.setRequestMethod("POST")
    if (token.isDefined) {
      conn.addRequestProperty("Authorization", "Bearer " + token.get)
    }
    conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.setRequestProperty("User-Agent", BuildInfo.nameWithVersion)
    conn.setDoInput(true)
    conn.setDoOutput(true)

    val postData = "volumeIDs=" + ids.toIterator.map(URLEncoder.encode(_, codec.name)).mkString("|")

    Using.resource(conn.getOutputStream) { outputStream =>
      outputStream.write(postData.getBytes(codec.charSet))
    }

    conn.getResponseCode match {
      case HttpURLConnection.HTTP_OK =>
        useTempStorage match {
          case Some(tmpDir) =>
            logger.debug("Saving response...")
            val tmpPath = FileUtils.saveToTempFile(conn.getInputStream, "dataapi", ".zip",
              tmpDir, FileUtils.restrictedOwnerOnlyAccess(tmpDir)).get
            conn.disconnect()
            logger.debug(s"Response saved to $tmpPath")
            VolumeIterator(tmpPath.toFile, failOnError).onClose {
              Try {
                Files.delete(tmpPath)
                logger.debug(s"Deleted temp response file: $tmpPath")
              }.recover {
                case t => logger.error(s"Could not delete temp response file: $tmpPath", t)
              }
            }

          case None => VolumeIterator(new ZipInputStream(conn.getInputStream, codec.charSet))
        }

      case HttpURLConnection.HTTP_NOT_FOUND =>
        throw ApiRequestException(HttpURLConnection.HTTP_NOT_FOUND, apiUrl.toString)

      case errCode =>
        val message =
          Option(conn.getErrorStream)
            .map(Source.fromInputStream(_).mkString)
            .getOrElse("")

        throw ApiRequestException(errCode, message)
    }
  }
}
