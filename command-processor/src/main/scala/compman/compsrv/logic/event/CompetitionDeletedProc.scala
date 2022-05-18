package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionDeletedEvent, Event}
import compservice.model.protobuf.model.CompetitionStatus

object CompetitionDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case _: CompetitionDeletedEvent => deleteCompetition[F](state) }

  private def deleteCompetition[F[+_]: Monad: IdOperations: EventOperations](
                                                                  state: CompetitionState
  ): F[Option[CompetitionState]] = { Monad[F].pure(Option(state.updateStatus(CompetitionStatus.DELETED))) }
}
