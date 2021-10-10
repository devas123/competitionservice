package compman.compsrv.logic

import compman.compsrv.logic.logging.CompetitionLogging.Service

package object logging {
  def info[F[+_]: Service](msg: => String): F[Unit]                                = Service[F].info(msg)
  def info[F[+_]: Service](msg: => String, args: Any*): F[Unit]                    = Service[F].info(msg, args)
  def info[F[+_]: Service](error: Throwable, msg: => String, args: Any*): F[Unit]  = Service[F].info(error, msg, args)
  def error[F[+_]: Service](msg: => String, args: Any*): F[Unit]                   = Service[F].error(msg, args)
  def error[F[+_]: Service](error: Throwable, msg: => String, args: Any*): F[Unit] = Service[F].error(error, msg, args)
  def error[F[+_]: Service](msg: => String): F[Unit]                               = Service[F].error(msg)
  def warn[F[+_]: Service](msg: => String, args: Any*): F[Unit]                    = Service[F].warn(msg, args)
  def warn[F[+_]: Service](error: Throwable, msg: => String, args: Any*): F[Unit] = Service[F]
    .warn(error: Throwable, msg, args)
  def warn[F[+_]: Service](msg: => String): F[Unit]                                = Service[F].warn(msg)
  def debug[F[+_]: Service](msg: => String, args: Any*): F[Unit]                   = Service[F].debug(msg, args)
  def debug[F[+_]: Service](error: Throwable, msg: => String, args: Any*): F[Unit] = Service[F].debug(error, msg, args)
  def debug[F[+_]: Service](msg: => String): F[Unit]                               = Service[F].debug(msg)
}
