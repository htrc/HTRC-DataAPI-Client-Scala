package org.hathitrust.htrc.tools.dataapi

import java.util.zip.{ZipEntry, ZipInputStream}

import org.hathitrust.htrc.textprocessing.runningheaders.Page
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
  private var currentCleanVolId = nextEntry.map(_.getName.takeWhile(_ != '/'))

  override def hasNext: Boolean = nextEntry.isDefined

  override def next(): HTRCVolume = nextEntry match {
    case Some(zipEntry) if !zipEntry.getName.equals("ERROR.err") =>
      val pages = Iterator
        .continually(nextEntry)
        .takeWhile(e => e.isDefined && e.get.getName.startsWith(currentCleanVolId.get + "/"))
        .map(_.get)
        .withFilter { e =>
          if (e.isDirectory) {
            advance()
            false
          } else
            true
        }
        .map(readPageAndAdvance)
        .toSeq
        .sortBy(_.pageSeq)

      val cleanId = currentCleanVolId.get
      currentCleanVolId = nextEntry.map(_.getName.takeWhile(_ != '/'))

      new HTRCVolume(PairtreeHelper.getDocFromCleanId(cleanId), pages)

    case Some(_) =>
      val error = Source.fromInputStream(zipStream).mkString
      throw new Exception(error)

    case None => throw new NoSuchElementException
  }

  def close(): Unit = zipStream.close()

  private def readPageAndAdvance(entry: ZipEntry): Page = {
    val pageSeqRegex(seq) = entry.getName
    val page = Page(zipStream, seq)

    advance()

    page
  }

  private def advance(): Unit = {
    nextEntry = Option(zipStream.getNextEntry)
  }
}