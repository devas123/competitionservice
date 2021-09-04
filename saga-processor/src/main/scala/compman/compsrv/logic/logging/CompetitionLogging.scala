package compman.compsrv.logic.logging

import zio.{RIO, Task, ZLayer}
import zio.logging.{log, LogAnnotation, LogContext, Logging}
import zio.logging.slf4j.Slf4jLogger

import java.io.{PrintWriter, StringWriter}

object CompetitionLogging {
  trait Service[F[+_]] {
    def info(msg: => String): F[Unit]
    def info(msg: => String, args: Any*): F[Unit]
    def info(error: Throwable, msg: => String, args: Any*): F[Unit]
    def error(msg: => String, args: Any*): F[Unit]
    def error(error: Throwable, msg: => String, args: Any*): F[Unit]
    def error(msg: => String): F[Unit]
    def warn(msg: => String, args: Any*): F[Unit]
    def warn(error: Throwable, msg: => String, args: Any*): F[Unit]
    def warn(msg: => String): F[Unit]
    def debug(msg: => String, args: Any*): F[Unit]
    def debug(error: Throwable, msg: => String, args: Any*): F[Unit]
    def debug(msg: => String): F[Unit]
  }

  object Service {
    def apply[F[+_]](implicit F: Service[F]): Service[F] = F
  }

  type LIO[+A] = RIO[zio.logging.Logging, A]

  implicit class ThrowableOps(t: Throwable) {
    def getStackTraceStr: String = {
      val sw = new StringWriter()
      t.printStackTrace(new PrintWriter(sw))
      sw.getBuffer.toString
    }
  }

  object Annotations {
    val competitionId: LogAnnotation[Option[String]] = LogAnnotation[Option[String]](
      name = "competition-id",
      initialValue = None,
      combine = (_, newValue) => newValue,
      render = _.getOrElse("undefined")
    )
  }
  object Live {

    val loggingLayer: ZLayer[Any, Nothing, Logging] = Slf4jLogger.make { (context, message) =>
      val correlationId = LogAnnotation.CorrelationId.render(context.get(LogAnnotation.CorrelationId))
      val competitionId = Annotations.competitionId.render(context.get(Annotations.competitionId))
      "[competition-id = %s, correlation-id = %s] %s".format(competitionId, correlationId, message)
    }

    def withContext[A](fa: LogContext => LogContext)(action: LIO[A]): LIO[A] = log.locally(fa)(action)

    val live: Service[LIO] = new Service[LIO] {
      import zio.logging._
      override def info(msg: => String): LIO[Unit]             = log.info(msg)
      override def info(msg: => String, args: Any*): LIO[Unit] = log.info(msg.format(args))
      override def info(error: Throwable, msg: => String, args: Any*): LIO[Unit] = for {
        _ <- info(msg, args)
        _ <- info(s"${error.getStackTraceStr}\n")
      } yield ()
      override def error(msg: => String, args: Any*): LIO[Unit] = log.error(msg.format(args))
      override def error(msg: => String): LIO[Unit]             = log.error(msg)
      override def error(error: Throwable, msg: => String, args: Any*): LIO[Unit] = for {
        _ <- log.error(msg.format(args))
        _ <- log.error(s"${error.getStackTraceStr}\n")
      } yield ()

      override def warn(msg: => String, args: Any*): LIO[Unit] = log.warn(msg.format(args))
      override def warn(error: Throwable, msg: => String, args: Any*): LIO[Unit] = for {
        _ <- log.warn(msg.format(args))
        _ <- log.warn(s"${error.getStackTraceStr}\n")
      } yield ()
      override def warn(msg: => String): LIO[Unit]              = log.warn(msg)
      override def debug(msg: => String, args: Any*): LIO[Unit] = log.debug(msg.format(args))
      override def debug(error: Throwable, msg: => String, args: Any*): LIO[Unit] = for {
        _ <- log.debug(msg.format(args))
        _ <- log.debug(s"${error.getStackTraceStr}\n")
      } yield ()
      override def debug(msg: => String): LIO[Unit] = log.debug(msg)
    }

  }

}
