package compman.compsrv

import cats.data.EitherT
import cats.Monad
import compman.compsrv.model.Errors

package object logic {
  def assertE(condition: => Boolean, message: => Option[String] = None): Either[Errors.Error, Unit] =
    if (condition)
      Right(())
    else
      Left(Errors.InternalError(message))

  def assertEErr(condition: => Boolean, error: => Errors.Error): Either[Errors.Error, Unit] =
    if (condition)
      Right(())
    else
      Left(error)

  def assertET[F[+_]: Monad](condition: => Boolean, message: => Option[String] = None): EitherT[F, Errors.Error, Unit] =
    EitherT.fromEither(assertE(condition, message))
  def assertETErr[F[+_]: Monad](condition: => Boolean, error: => Errors.Error): EitherT[F, Errors.Error, Unit] =
    EitherT.fromEither(assertEErr(condition, error))
}
