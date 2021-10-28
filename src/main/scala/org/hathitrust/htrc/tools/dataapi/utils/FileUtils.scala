package org.hathitrust.htrc.tools.dataapi.utils

import java.io.{InputStream, OutputStream}
import java.nio.file._
import java.nio.file.attribute._
import scala.util.{Try, Using}

object FileUtils {
  val OSTmpDir: String = System.getProperty("java.io.tmpdir")
  private val BUFFER_SIZE = 16384

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
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

  def restrictedOwnerOnlyAccess(path: String): List[FileAttribute[_]] =
    restrictedOwnerOnlyAccess(Paths.get(path))

  def restrictedOwnerOnlyAccess(path: Path): List[FileAttribute[_]] = {
    val fileStore = Files.getFileStore(path)
    if (fileStore.supportsFileAttributeView(classOf[PosixFileAttributeView]))
      List(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    else Nil
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def saveToTempFile(is: InputStream,
                     prefix: String = null,
                     suffix: String = null,
                     tmpDir: String = OSTmpDir,
                     fileAttributes: List[FileAttribute[_]] = Nil): Try[Path] = Try {
    val tmpPath = Paths.get(tmpDir)
    val tmpFile = Files.createTempFile(tmpPath, prefix, suffix, fileAttributes: _*)

    Using(Files.newOutputStream(tmpFile, StandardOpenOption.WRITE)) { tmpStream =>
      copy(is, tmpStream)
    }

    tmpFile
  }

}
