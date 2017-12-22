package org.hathitrust.htrc.tools.dataapi.utils

import scala.language.reflectiveCalls

object AutoCloseableResource {

  def using[A, B <: {def close() : Unit}](closeable: B)(f: B => A): A =
    try {
      f(closeable)
    }
    finally {
      closeable.close()
    }

}
