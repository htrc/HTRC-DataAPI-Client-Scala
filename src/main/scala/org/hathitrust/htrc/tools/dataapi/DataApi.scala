package org.hathitrust.htrc.tools.dataapi

import scala.collection.compat.IterableOnce
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Codec

trait DataApi {

  def retrieveVolumes(ids: IterableOnce[String])
                     (implicit codec: Codec, executionContext: ExecutionContext): Future[VolumeIterator]

}
