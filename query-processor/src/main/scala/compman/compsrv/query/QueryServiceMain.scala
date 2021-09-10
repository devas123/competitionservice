package compman.compsrv.query
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.CompetitionApiActor
import compman.compsrv.query.service.CompetitionHttpApiService
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.interop.catz._

object QueryServiceMain extends zio.App {

  val server: ZIO[zio.ZEnv, Throwable, Unit] = for {
    actorSystem <- ActorSystem("test")
    actor <- actorSystem
      .make("queryApiActor", ActorConfig(), CompetitionApiActor.initialState, CompetitionApiActor.behavior)
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[Task].bindHttp(8080, "localhost").withHttpApp(CompetitionHttpApiService.service(actor)).serve
        .compile.drain
    }
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server
    .fold(_ => ExitCode.failure, _ => ExitCode.success)
}
