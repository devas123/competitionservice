package compman.compsrv.logic.snapshot

import compman.compsrv.model.CompetitionState

object SnapshotService {
  trait Service[F[+_]] {
    def saveSnapshot(state: CompetitionState): F[Unit]
    def loadSnapshot(id: String): F[Option[CompetitionState]]
  }
}
