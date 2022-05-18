package compman.compsrv.logic

import compman.compsrv.Utils
import compservice.model.protobuf.model.{CategoryDescriptor, CompetitionProperties, CompetitionStatus, Competitor, FightDescription, RegistrationInfo, Schedule, StageDescriptor}
import monocle.macros.GenLens
import monocle.Lens

final case class CompetitionState(
  id: String,
  competitors: Option[Map[String, Competitor]],
  competitionProperties: Option[CompetitionProperties],
  stages: Option[Map[String, StageDescriptor]],
  fights: Option[Map[String, FightDescription]],
  categories: Option[Map[String, CategoryDescriptor]],
  registrationInfo: Option[RegistrationInfo],
  schedule: Option[Schedule],
  revision: Int
)

object CompetitionState {
  private val fightsLens         = GenLens[CompetitionState](_.fights)
  private val stagesLens         = GenLens[CompetitionState](_.stages)
  private val compPropertiesLens = GenLens[CompetitionState](_.competitionProperties)
  private val categoriesLens     = GenLens[CompetitionState](_.categories)
  private val competitorsLens    = GenLens[CompetitionState](_.competitors)

  final implicit class CompetitionStateOps(private val c: CompetitionState) extends AnyVal {
    def updateStatus(competitionStatus: CompetitionStatus): CompetitionState = compPropertiesLens
      .modify(p => p.map(_.withStatus(competitionStatus)))(c)
    def deleteCategory(id: String): CompetitionState = categoriesLens.modify(p => p.map(_ - id))(c)
    def updateFights(fights: Seq[FightDescription]): CompetitionState = fightsLens
      .modify(f => f.map(f => f ++ Utils.groupById(fights)(_.id)))(c)
    def fightsApply(
      update: Option[Map[String, FightDescription]] => Option[Map[String, FightDescription]]
    ): CompetitionState = lensApplyOptionsl(fightsLens)(update)
    def competitorsApply(
      update: Option[Map[String, Competitor]] => Option[Map[String, Competitor]]
    ): CompetitionState = lensApplyOptionsl(competitorsLens)(update)

    private def lensApplyOptionsl[T](lens: Lens[CompetitionState, Option[Map[String, T]]])(
      update: Option[Map[String, T]] => Option[Map[String, T]]
    ) = lens.modify(update)(c)

    def updateStage(stage: StageDescriptor): CompetitionState = stagesLens
      .modify(s => s.map(stgs => stgs + (stage.id -> stage)))(c)
  }
}
