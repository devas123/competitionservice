package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightOrderChangedEvent}
import compman.compsrv.query.model.{Fight, FightOrderUpdateExtended, Mat}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, FightUpdateOperations}
import compman.compsrv.Utils
import compman.compsrv.logic.fight.CommonFightUtils
import compman.compsrv.logic.fight.CommonFightUtils.{FightView, MatView}

object FightOrderChangedProc {
  import cats.implicits._

  private def asFightView(fight: Fight): FightView = FightView(
    fight.id,
    MatView(fight.matId.orNull, fight.matName, fight.periodId.orNull, fight.matOrder.getOrElse(-1)),
    fight.numberOnMat.getOrElse(-1),
    fight.durationSeconds.longValue(),
    fight.categoryId,
    fight.startTime.map(_.toInstant).orNull
  )

  private def asFightViews(fights: Map[String, Fight]): Map[String, FightView] = fights.view
    .mapValues(asFightView).toMap


  def apply[F[+_]: Monad: FightQueryOperations: FightUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightOrderChangedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: FightQueryOperations: CompetitionQueryOperations](
    event: FightOrderChangedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      fight         <- OptionT(FightQueryOperations[F].getFightById(competitionId)(categoryId, payload.getFightId))
      oldMatId      <- OptionT.fromOption[F](fight.matId)
      newMatFights  <- OptionT.liftF(FightQueryOperations[F].getFightsByMat(competitionId)(payload.getNewMatId, 1000))
      oldMatFights <-
        if (oldMatId == payload.getNewMatId) OptionT.fromOption[F](Some(List.empty[Fight]))
        else OptionT.liftF(FightQueryOperations[F].getFightsByMat(competitionId)(oldMatId, 1000))
      matFights = Utils.groupById(oldMatFights ++ newMatFights)(_.id)
      updates = CommonFightUtils.generateUpdates(payload, asFightView(fight), asFightViews(matFights))
        .map(upd => {
          val f = matFights(upd._1)
          FightOrderUpdateExtended(competitionId, upd._2, Mat(f.matId.orNull, f.matName.orNull, f.matOrder.getOrElse(-1)))
        })
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightOrderAndMat(updates.toList))
    } yield ()
  }.value.map(_ => ())
}
