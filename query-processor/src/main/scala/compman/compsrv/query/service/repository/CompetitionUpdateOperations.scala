package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import org.mongodb.scala.{Document, MongoClient}
import zio.{Ref, RIO, ZIO}

trait CompetitionUpdateOperations[F[+_]] {
  def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): F[Unit]
  def addCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit]
  def updateCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit]
  def removeCompetitionProperties(id: String): F[Unit]
  def addCompetitionInfoTemplate(competitionId: String)(competitionInfoTemplate: CompetitionInfoTemplate): F[Unit]
  def removeCompetitionInfoTemplate(competitionId: String): F[Unit]
  def addStage(stageDescriptor: StageDescriptor): F[Unit]
  def updateStage(stageDescriptor: StageDescriptor): F[Unit]
  def removeStages(competition: String)(categoryId: String): F[Unit]
  def updateStageStatus(competitionId: String)(categoryId: String, stageId: String, newStatus: StageStatus): F[Unit]
  def addCategory(category: Category): F[Unit]
  def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): F[Unit]
  def removeCategory(competitionId: String)(id: String): F[Unit]
  def addCompetitor(competitor: Competitor): F[Unit]
  def updateCompetitor(competitor: Competitor): F[Unit]
  def removeCompetitor(competitionId: String)(id: String): F[Unit]
  def addRegistrationGroup(group: RegistrationGroup): F[Unit]
  def addRegistrationGroups(groups: List[RegistrationGroup]): F[Unit]
  def updateRegistrationGroup(group: RegistrationGroup): F[Unit]
  def updateRegistrationGroups(groups: List[RegistrationGroup]): F[Unit]
  def removeRegistrationGroup(competitionId: String)(id: String): F[Unit]
  def addRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriod(period: RegistrationPeriod): F[Unit]
  def updateRegistrationPeriods(periods: List[RegistrationPeriod]): F[Unit]
  def removeRegistrationPeriod(competitionId: String)(id: String): F[Unit]
  def addPeriod(entry: Period): F[Unit]
  def addPeriods(entries: List[Period]): F[Unit]
  def updatePeriods(entries: List[Period]): F[Unit]
  def removePeriod(competitionId: String)(id: String): F[Unit]
  def removePeriods(competitionId: String): F[Unit]
}

object CompetitionUpdateOperations {
  def apply[F[+_]](implicit F: CompetitionUpdateOperations[F]): CompetitionUpdateOperations[F] = F

  import cats.implicits._
  import zio.interop.catz._

  def test(
    competitionProperties: Option[Ref[Map[String, CompetitionProperties]]] = None,
    categories: Option[Ref[Map[String, Category]]] = None,
    competitors: Option[Ref[Map[String, Competitor]]] = None,
    periods: Option[Ref[Map[String, Period]]] = None,
    registrationPeriods: Option[Ref[Map[String, RegistrationPeriod]]] = None,
    registrationGroups: Option[Ref[Map[String, RegistrationGroup]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ): CompetitionUpdateOperations[LIO] = new CompetitionUpdateOperations[LIO] with CommonTestOperations {

    override def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): LIO[Unit] = {
      comPropsUpdate(competitionProperties)(competitionId)(_.copy(registrationOpen = isOpen))
    }

    override def addCompetitionProperties(newProperties: CompetitionProperties): LIO[Unit] = competitionProperties
      .map(_.update(m => m.updated(newProperties.id, newProperties))).getOrElse(ZIO.unit)

    override def updateCompetitionProperties(competitionProperties: CompetitionProperties): LIO[Unit] =
      addCompetitionProperties(competitionProperties)

    override def removeCompetitionProperties(id: String): LIO[Unit] = competitionProperties.map(_.update(m => m - id))
      .getOrElse(ZIO.unit)

    override def addCompetitionInfoTemplate(competitionId: String)(newTemplate: CompetitionInfoTemplate): LIO[Unit] =
      comPropsUpdate(competitionProperties)(competitionId)(_.copy(infoTemplate = newTemplate))

    override def removeCompetitionInfoTemplate(competitionId: String): LIO[Unit] =
      comPropsUpdate(competitionProperties)(competitionId)(_.copy(infoTemplate = CompetitionInfoTemplate(Array.empty)))

    override def addStage(stageDescriptor: StageDescriptor): LIO[Unit] =
      add(stages)(stageDescriptor.id)(Some(stageDescriptor))

    override def updateStage(stageDescriptor: StageDescriptor): LIO[Unit] =
      stagesUpdate(stages)(stageDescriptor.id)(_ => stageDescriptor)

    override def updateStageStatus(
      competitionId: String
    )(categoryId: String, stageId: String, newStatus: StageStatus): LIO[Unit] =
      stagesUpdate(stages)(stageId)(_.copy(stageStatus = newStatus))

    override def addCategory(category: Category): LIO[Unit] = add(categories)(category.id)(Some(category))

    override def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): LIO[Unit] =
      update(categories)(id)(_.copy(registrationOpen = newStatus))

    override def removeCategory(competitionId: String)(id: String): LIO[Unit] = remove(categories)(id)

    override def addCompetitor(competitor: Competitor): LIO[Unit] = add(competitors)(competitor.id)(Some(competitor))

    override def updateCompetitor(competitor: Competitor): LIO[Unit] =
      update(competitors)(competitor.id)(_ => competitor)

    override def removeCompetitor(competitionId: String)(id: String): LIO[Unit] = remove(competitors)(id)

    override def addRegistrationGroup(group: RegistrationGroup): LIO[Unit] =
      add(registrationGroups)(group.id)(Some(group))

    override def addRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = groups
      .traverse(addRegistrationGroup).map(_ => ())

    override def updateRegistrationGroup(group: RegistrationGroup): LIO[Unit] =
      update(registrationGroups)(group.id)(_ => group)

    override def updateRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = groups
      .traverse(updateRegistrationGroup).map(_ => ())

    override def removeRegistrationGroup(competitionId: String)(id: String): LIO[Unit] = remove(registrationGroups)(id)

    override def addRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] =
      add(registrationPeriods)(period.id)(Some(period))

    override def updateRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] =
      update(registrationPeriods)(period.id)(_ => period)

    override def updateRegistrationPeriods(periods: List[RegistrationPeriod]): LIO[Unit] = periods
      .traverse(updateRegistrationPeriod).map(_ => ())

    override def removeRegistrationPeriod(competitionId: String)(id: String): LIO[Unit] =
      remove(registrationPeriods)(id)

    override def addPeriod(entry: Period): LIO[Unit] = add(periods)(entry.id)(Some(entry))

    override def addPeriods(entries: List[Period]): LIO[Unit] = entries.traverse(addPeriod).map(_ => ())

    override def updatePeriods(entries: List[Period]): LIO[Unit] = entries.traverse(e => update(periods)(e.id)(_ => e))
      .map(_ => ())

    override def removePeriod(competitionId: String)(id: String): LIO[Unit] = remove(periods)(id)

    override def removePeriods(competitionId: String): LIO[Unit] = periods
      .map(_.update(m => m.filter { case (_, p) => p.competitionId == competitionId })).getOrElse(ZIO.unit)

    override def removeStages(competition: String)(categoryId: String): LIO[Unit] = stages
      .map(_.update(s => s.filter(_._2.categoryId != categoryId))).getOrElse(ZIO.unit)
  }

  def live(mongo: MongoClient, name: String)(implicit
    log: CompetitionLogging.Service[LIO]
  ): CompetitionUpdateOperations[LIO] = new CompetitionUpdateOperations[LIO] with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._
    override def updateRegistrationOpen(competitionId: String)(isOpen: Boolean): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), set("properties.registrationOpen", isOpen))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addCompetitionProperties(competitionProperties: CompetitionProperties): LIO[Unit] = {
      val statement = competitionStateCollection.insertOne(
        CompetitionState(competitionProperties.id, competitionProperties, Map.empty, Map.empty, Map.empty, None)
      )
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateCompetitionProperties(competitionProperties: CompetitionProperties): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionProperties.id), set("properties", competitionProperties))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeCompetitionProperties(id: String): LIO[Unit] = {
      val statement = competitionStateCollection.deleteOne(equal(idField, id))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addCompetitionInfoTemplate(
      competitionId: String
    )(competitionInfoTemplate: CompetitionInfoTemplate): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), set("properties.infoTemplate", competitionInfoTemplate))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeCompetitionInfoTemplate(competitionId: String): LIO[Unit] =
      addCompetitionInfoTemplate(competitionId)(CompetitionInfoTemplate(Array.empty))

    override def addStage(stageDescriptor: StageDescriptor): LIO[Unit] = {
      val statement = competitionStateCollection.findOneAndUpdate(
        equal(idField, stageDescriptor.competitionId),
        set(s"stages.${stageDescriptor.id}", stageDescriptor)
      )
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateStage(stageDescriptor: StageDescriptor): LIO[Unit] = addStage(stageDescriptor)

    override def removeStages(competition: String)(categoryId: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competition), set(s"stages", Document()))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateStageStatus(
      competitionId: String
    )(categoryId: String, stageId: String, newStatus: StageStatus): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), set(s"stages.$stageId.stageStatus", newStatus))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addCategory(category: Category): LIO[Unit] = {
      val statement = competitionStateCollection.findOneAndUpdate(
        equal(idField, category.competitionId),
        set(s"categories.${category.id}", category)
      )
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), set(s"categories.$id.registrationOpen", newStatus))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeCategory(competitionId: String)(id: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), unset(s"categories.$id"))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addCompetitor(competitor: Competitor): LIO[Unit] = {
      val statement = competitorCollection.insertOne(competitor)
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateCompetitor(competitor: Competitor): LIO[Unit] = {
      val statement = competitorCollection.replaceOne(equal(idField, competitor.id), competitor)
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeCompetitor(competitionId: String)(id: String): LIO[Unit] = {
      val statement = competitorCollection.deleteOne(equal(idField, id))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addRegistrationGroup(group: RegistrationGroup): LIO[Unit] = {
      val statement = competitionStateCollection.findOneAndUpdate(
        equal(idField, group.competitionId),
        set(s"registrationInfo.registrationGroups.${group.id}", group)
      )
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = {
      val updates = groups.map(group => set(s"registrationInfo.registrationGroups.${group.id}", group))
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, groups.head.competitionId), combine(updates: _*))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateRegistrationGroup(group: RegistrationGroup): LIO[Unit]         = addRegistrationGroup(group)
    override def updateRegistrationGroups(groups: List[RegistrationGroup]): LIO[Unit] = addRegistrationGroups(groups)

    override def removeRegistrationGroup(competitionId: String)(id: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), unset(s"registrationInfo.registrationGroups.$id"))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] = {
      val statement = competitionStateCollection.findOneAndUpdate(
        equal(idField, period.competitionId),
        set(s"registrationInfo.registrationPeriods.${period.id}", period)
      )
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateRegistrationPeriod(period: RegistrationPeriod): LIO[Unit] = addRegistrationPeriod(period)
    override def updateRegistrationPeriods(periods: List[RegistrationPeriod]): LIO[Unit] = {
      val updates = periods.map(period => set(s"registrationInfo.registrationPeriods.${period.id}", period))
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, periods.head.competitionId), combine(updates: _*))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeRegistrationPeriod(competitionId: String)(id: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), unset(s"registrationInfo.registrationPeriods.$id"))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addPeriod(entry: Period): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, entry.competitionId), set(s"periods.${entry.id}", entry))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addPeriods(entries: List[Period]): LIO[Unit] = {
      val updates = entries.map(period => set(s"periods.${period.id}", period))
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, entries.head.competitionId), combine(updates: _*))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updatePeriods(entries: List[Period]): LIO[Unit] = addPeriods(entries)

    override def removePeriod(competitionId: String)(id: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), unset(s"periods.$id"))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removePeriods(competitionId: String): LIO[Unit] = {
      val statement = competitionStateCollection
        .findOneAndUpdate(equal(idField, competitionId), set(s"periods", Document()))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }
  }
}
