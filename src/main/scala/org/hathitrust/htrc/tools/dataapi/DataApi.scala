package org.hathitrust.htrc.tools.dataapi

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Codec

trait DataApi {

  def retrieveVolumes(ids: TraversableOnce[String])
                     (implicit codec: Codec, executionContext: ExecutionContext): Future[VolumeIterator]

}
