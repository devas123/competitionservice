
package compman.compsrv.gateway.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.gateway.GatewayServiceMain.ServiceIO
import compman.compsrv.gateway.json.ObjectMapperFactory
import compman.compsrv.model.commands.CommandDTO
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, QueryParamDecoder}
import zio.Task
import zio.duration.{Duration, durationInt}
import zio.interop.catz._
import zio.kafka.producer.Producer
import zio.logging.Logging
import scala.concurrent.ExecutionContext.global

object GatewayService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    implicit val stringQueryParamDecoder: QueryParamDecoder[String] = QueryParamDecoder[String]
    implicit val intQueryParamDecoder: QueryParamDecoder[Int] = QueryParamDecoder[Int]

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  val timeout: Duration = 10.seconds
  val decoder: ObjectMapper = ObjectMapperFactory.createObjectMapper


  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(): HttpRoutes[ServiceIO] = HttpRoutes
    .of[ServiceIO] {
      case req@POST -> Root / "command" =>
        for {
          body <- req.body.covary[ServiceIO].chunkAll.compile.toList
          command <- Task(decoder.readValue(body.flatMap(_.toList).toArray, classOf[CommandDTO]))
          _ <- Logging.info(s"Sending command $command")
          _ <- Producer.produce[Any, String, Array[Byte]]("competition-commands", command.getCompetitionId, decoder.writeValueAsBytes(command))
          resp <- Ok()
        } yield resp

      case req@GET -> Root / "query" =>
        for {
          response <- BlazeClientBuilder
            [ServiceIO](global).resource.use { client =>
            client.expect[Array[Byte]](s"http://localhost:8080/querysrv${req.pathInfo.normalize.toString()}")
          }
          r <- Ok(response)
        } yield r
    }
}
