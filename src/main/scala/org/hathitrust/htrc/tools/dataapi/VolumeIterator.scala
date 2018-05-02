package org.hathitrust.htrc.tools.dataapi

import java.util.zip.{ZipEntry, ZipInputStream}

import org.hathitrust.htrc.data.{HtrcPage, HtrcVolume, HtrcVolumeId}
import org.hathitrust.htrc.tools.dataapi.exceptions.ApiRequestException

import scala.io.{Codec, Source}

object VolumeIterator {
  private val pageSeqRegex = """[^/]+/\D*(\d+)\.txt""".r

  def apply(zs: ZipInputStream)(implicit codec: Codec): VolumeIterator =
    new VolumeIterator(zs)

  def apply(zs: ZipInputStream, closeF: ZipInputStream => Unit)(implicit codec: Codec): VolumeIterator =
    new VolumeIterator(zs, closeF)
}

class VolumeIterator(zipStream: ZipInputStream, closeF: ZipInputStream => Unit = _.close())
                    (implicit codec: Codec) extends Iterator[HtrcVolume] with AutoCloseable {
  import VolumeIterator._

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var nextEntry = Option(zipStream.getNextEntry)

  override def hasNext: Boolean = nextEntry.isDefined

  @SuppressWarnings(Array(
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.TryPartial",
    "org.wartremover.warts.Throw"
  ))
  override def next(): HtrcVolume = nextEntry match {
    case Some(zipEntry) if !zipEntry.getName.equals("ERROR.err") =>
      require(zipEntry.isDirectory)
      val cleanId = zipEntry.getName.init
      val pages = Iterator
        .continually({
          nextEntry = Option(zipStream.getNextEntry)
          nextEntry
        })
        .takeWhile(e => e.isDefined && !e.get.isDirectory && !e.get.getName.equals("ERROR.err"))
        .map(_.get)
        .map(readPage)
        .toIndexedSeq
        .sortBy(_.seq)

      val volumeId = HtrcVolumeId.parseClean(cleanId).get
      new HtrcVolume(volumeId, pages)

    case Some(_) =>
      val error = Source.fromInputStream(zipStream).mkString
      throw ApiRequestException(500, error)

    case None => throw new NoSuchElementException
  }

  def close(): Unit = closeF(zipStream)

  private def readPage(entry: ZipEntry): HtrcPage = {
    val pageSeqRegex(seq) = entry.getName
    new HtrcPage(seq, zipStream)
  }
}