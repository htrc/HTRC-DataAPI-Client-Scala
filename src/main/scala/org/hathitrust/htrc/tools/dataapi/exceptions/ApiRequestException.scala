package org.hathitrust.htrc.tools.dataapi.exceptions

@SuppressWarnings(Array("org.wartremover.warts.Null"))
case class ApiRequestException(code: Int, message: String, cause: Throwable = null)
  extends Exception(s"($code) $message", cause)