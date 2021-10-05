package compman.compsrv.gateway.service

import compman.compsrv.gateway.GatewayServiceMain.ServiceIO
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.{Task, ZIO}
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.kafka.producer.Producer
import zio.logging.Logging

object GatewayService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object CompetitionIdParamMatcher extends OptionalQueryParamDecoderMatcher[String]("competitionId")
  }

  val timeout: Duration = 10.seconds

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._
  import QueryParameters._

  def service(): HttpRoutes[ServiceIO] = HttpRoutes
    .of[ServiceIO] { case req @ POST -> Root / "competition" / "command" :? CompetitionIdParamMatcher(competitionId) =>
      for {
        _ <-
          if (competitionId.isEmpty && !competitionId.contains(null)) ZIO
            .fail(new RuntimeException("competition id missing"))
          else ZIO.unit
        body    <- req.body.covary[ServiceIO].chunkAll.compile.toList
        command <- Task(body.flatMap(_.toList).toArray)
        _       <- Logging.info(s"Sending command for $competitionId")
        _       <- Producer.produce[Any, String, Array[Byte]]("competition-commands", competitionId.get, command)
        resp    <- Ok()
      } yield resp
    }
}