package org.hathitrust.htrc.tools.dataapi.utils

import java.io.{InputStream, OutputStream}
import java.nio.file._
import java.nio.file.attribute._

import org.hathitrust.htrc.tools.dataapi.utils.AutoCloseableResource._

import scala.util.Try

object FileUtils {
  val OSTmpDir: String = System.getProperty("java.io.tmpdir")
  private val BUFFER_SIZE = 16384

  def copy(source: InputStream, sink: OutputStream): Try[Long] = Try {
    val buf = new Array[Byte](BUFFER_SIZE)
    var numRead = 0L
    var n = source.read(buf)
    while (n > 0) {
      sink.write(buf, 0, n)
      numRead += n
      n = source.read(buf)
    }

    numRead
  }

  def readOnlyAttributes(path: String): FileAttribute[_] =
    readOnlyAttributes(Paths.get(path))

  def readOnlyAttributes(path: Path): FileAttribute[_] = {
    val fileStore = Files.getFileStore(path)
    if (fileStore.supportsFileAttributeView(classOf[PosixFileAttributeView]))
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
    else {
      if (fileStore.supportsFileAttributeView(classOf[DosFileAttributeView]))
        new FileAttribute[Boolean] {
          override def name(): String = "dos:readonly"
          override def value(): Boolean = true
        }
      else null
    }
  }

  def saveToTempFile(is: InputStream,
                     prefix: String = null,
                     suffix: String = null,
                     tmpDir: String = OSTmpDir,
                     fileAttributes: List[FileAttribute[_]] = Nil): Try[Path] = Try {
    val tmpPath = Paths.get(tmpDir)
    val tmpFile = Files.createTempFile(tmpPath, prefix, suffix, fileAttributes: _*)

    using(Files.newOutputStream(tmpFile, StandardOpenOption.WRITE)) { tmpStream =>
      copy(is, tmpStream)
    }

    tmpFile
  }

}
