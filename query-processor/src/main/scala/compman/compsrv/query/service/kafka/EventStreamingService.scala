package compman.compsrv.query.service.kafka

import compman.compsrv.model.events.EventDTO
import zio.stream.ZStream

object EventStreamingService {

  trait EventStreaming[R] {
    def getEventsStream(topic: String): ZStream[R, Throwable, EventDTO]
  }

}
