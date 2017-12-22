package org.hathitrust.htrc.tools.dataapi.exceptions

case class ApiRequestException(code: Int, message: String, cause: Throwable = null)
  extends Exception(message, cause)