package org.hathitrust.htrc.tools.dataapi

import java.io.{File, FileInputStream}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream}

import org.hathitrust.htrc.data.{HtrcPage, HtrcVolume, HtrcVolumeId}
import org.hathitrust.htrc.tools.dataapi.exceptions.ApiRequestException
import org.slf4j.{Logger, LoggerFactory}

import scala.io.{Codec, Source}
import scala.util.Try

object VolumeIterator {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val pageSeqRegex = """[^/]+/\D*(\d+)\.txt""".r

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(f: File)(implicit codec: Codec): VolumeIterator = {
    val zipFile = new ZipFile(f, codec.charSet)
    Option(zipFile.getEntry("ERROR.err")) match {
      case Some(entry) =>
        val error = Source.fromInputStream(zipFile.getInputStream(entry)).mkString
        Try(zipFile.close())
        throw ApiRequestException(500, error)

      case None =>
        zipFile.close()
        new VolumeIterator(new ZipInputStream(new FileInputStream(f), codec.charSet))
    }
  }

  def apply(zipStream: ZipInputStream)(implicit codec: Codec): VolumeIterator =
    new VolumeIterator(zipStream)
}

class VolumeIterator(zipStream: ZipInputStream)
                    (implicit codec: Codec) extends Iterator[HtrcVolume] with AutoCloseable {
  import VolumeIterator._

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var nextEntry = Option(zipStream.getNextEntry)
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var execOnClose: () => Unit = () => ()

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def hasNext: Boolean = nextEntry match {
    case Some(entry) if entry.isDirectory => true

    case Some(entry) if entry.getName.equalsIgnoreCase("ERROR.err") =>
      val error = Source.fromInputStream(zipStream).mkString
      throw ApiRequestException(500, error)

    case Some(entry) =>
      logger.debug("Ignoring ZIP entry: {}", entry.getName)
      nextEntry = Option(zipStream.getNextEntry)
      hasNext

    case None => false
  }

  @SuppressWarnings(Array(
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.TryPartial",
    "org.wartremover.warts.Throw"
  ))
  override def next(): HtrcVolume = nextEntry match {
    case Some(zipEntry) if zipEntry.isDirectory =>
      val cleanId = zipEntry.getName.init
      val pages = Iterator
        .continually({
          nextEntry = Option(zipStream.getNextEntry)
          nextEntry
        })
        .takeWhile {
          case Some(entry) if entry.getName.startsWith(zipEntry.getName) => true
          case _ => false
        }
        .map(_.get)
        .map(readPage)
        .toIndexedSeq
        .sortBy(_.seq)

      val volumeId = HtrcVolumeId.parseClean(cleanId).get
      new HtrcVolume(volumeId, pages)

    case _ => throw new NoSuchElementException
  }

  def onClose(block: => Unit): VolumeIterator = {
    execOnClose = () => block
    this
  }

  def close(): Unit = {
    try {
      zipStream.close()
    } finally {
      execOnClose()
    }
  }

  private def readPage(entry: ZipEntry): HtrcPage = {
    val pageSeqRegex(seq) = entry.getName
    new HtrcPage(seq, zipStream)
  }
}