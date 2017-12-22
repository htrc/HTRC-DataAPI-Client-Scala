package org.hathitrust.htrc.tools.dataapi

import java.util.zip.{ZipEntry, ZipInputStream}

import org.hathitrust.htrc.textprocessing.runningheaders.Page
import org.hathitrust.htrc.tools.dataapi.exceptions.ApiRequestException
import org.hathitrust.htrc.tools.pairtreehelper.PairtreeHelper
import org.hathitrust.htrc.tools.pairtreetotext.HTRCVolume

import scala.io.{Codec, Source}

object VolumeIterator {
  private val pageSeqRegex = """[^/]+/\D*(\d+)\.txt""".r

  def apply(zs: ZipInputStream)(implicit codec: Codec): VolumeIterator =
    new VolumeIterator(zs)
}

class VolumeIterator(zipStream: ZipInputStream)
                    (implicit codec: Codec) extends Iterator[HTRCVolume] with AutoCloseable {
  import VolumeIterator._

  private var nextEntry = Option(zipStream.getNextEntry)

  override def hasNext: Boolean = nextEntry.isDefined

  override def next(): HTRCVolume = nextEntry match {
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
        .toSeq
        .sortBy(_.pageSeq)

      new HTRCVolume(PairtreeHelper.getDocFromCleanId(cleanId), pages)

    case Some(_) =>
      val error = Source.fromInputStream(zipStream).mkString
      throw ApiRequestException(500, error)

    case None => throw new NoSuchElementException
  }

  def close(): Unit = zipStream.close()

  private def readPage(entry: ZipEntry): Page = {
    val pageSeqRegex(seq) = entry.getName
    Page(zipStream, seq)
  }
}