package compman.compsrv.query
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.CompetitionApiActor
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.service.CompetitionHttpApiService
import compman.compsrv.query.service.kafka.EventStreamingService
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.interop.catz._

object QueryServiceMain extends zio.App {
//  EventStreamingService.live(config.consumer.brokers), config.competitionEventListener.competitionNotificationsTopic
  val server: ZIO[zio.ZEnv, Throwable, Unit] = for {
    config <- AppConfig.load()
    actorSystem <- ActorSystem("test")
    actor <- actorSystem.make(
      "queryApiActor",
      ActorConfig(),
      CompetitionApiActor.initialState,
      CompetitionApiActor.behavior[Any]()
    )
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[Task].bindHttp(8080, "0.0.0.0").withHttpApp(CompetitionHttpApiService.service(actor)).serve
        .compile.drain
    }
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server
    .fold(_ => ExitCode.failure, _ => ExitCode.success)
}
