package compman.compsrv.query
import compman.compsrv.query.service.Hello1Service
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.interop.catz._

object QueryServiceMain extends zio.App {

  val server: ZIO[zio.ZEnv, Throwable, Unit] = ZIO.runtime[ZEnv]
    .flatMap {
      implicit rts =>
        BlazeServerBuilder[Task]
          .bindHttp(8080, "localhost")
          .withHttpApp(Hello1Service.service)
          .serve
          .compile
          .drain
    }


  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server.fold(_ => ExitCode.failure, _ => ExitCode.success)
}
