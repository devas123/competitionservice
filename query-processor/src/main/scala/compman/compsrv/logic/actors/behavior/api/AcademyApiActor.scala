package compman.compsrv.logic.actors.behavior.api

import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition._
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{AcademyOperations, Pagination}
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService
import org.mongodb.scala.MongoClient
import zio.Tag
import zio.logging.Logging

object AcademyApiActor {

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val academyService: AcademyService[LIO] = AcademyOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  trait ActorContext {
    implicit val logging: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val academyService: AcademyService[LIO]
  }

  sealed trait AcademyApiCommand {
    type responseType
    val replyTo: ActorRef[responseType]
  }

  final case class GetAcademies(searchString: Option[String], pagination: Option[Pagination])(
    override val replyTo: ActorRef[List[FullAcademyInfoDTO]]
  ) extends AcademyApiCommand {
    override type responseType = List[FullAcademyInfoDTO]
  }

  case class ActorState()
  val initialState: ActorState = ActorState()
  import Behaviors._
  def behavior[R: Tag](ctx: ActorContext): ActorBehavior[R with Logging, ActorState, AcademyApiCommand] = Behaviors
    .behavior[R with Logging, ActorState, AcademyApiCommand].withReceive { (_, _, state, command, _) =>
      {
        import ctx._
        for {
          _ <- Logging.info(s"Received academy API command $command")
          res <- command match {
            case c @ GetAcademies(_, _) => for {
                res <- AcademyOperations.getAcademies
                _   <- c.replyTo ! res.map(DtoMapping.toDtoFullAcademyInfo)
              } yield state
          }
        } yield res
      }
    }
}
