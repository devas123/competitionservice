package compman.compsrv.logic.actors.behavior.api

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import compman.compsrv.logic.actors.behavior.WithIORuntime
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{AcademyOperations, Pagination}
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService
import compservice.model.protobuf.query
import compservice.model.protobuf.query.{PageInfo, QueryServiceResponse}
import org.mongodb.scala.MongoClient

object AcademyApiActor {

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val academyService: AcademyService[IO] = AcademyOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  trait ActorContext extends WithIORuntime {
    implicit val academyService: AcademyService[IO]
  }

  sealed trait AcademyApiCommand {
    type responseType
    val replyTo: ActorRef[responseType]
  }

  final case class GetAcademies(searchString: Option[String], pagination: Option[Pagination])(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends AcademyApiCommand {
    override type responseType = QueryServiceResponse
  }

  final case class GetAcademy(id: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends AcademyApiCommand {
    override type responseType = QueryServiceResponse
  }

  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior(ctx: ActorContext): Behavior[AcademyApiCommand] = Behaviors.setup { _ =>
    import ctx._
    Behaviors.receive[AcademyApiCommand] { (_, command) =>
      command match {
        case c @ GetAcademy(id) => {
            for {
              res <- AcademyOperations.getAcademy(id)
              _ <- IO(
                c.replyTo ! QueryServiceResponse().withGetAcademyResponse(
                  query.GetAcademyResponse().update(_.academy.setIfDefined(res.map(DtoMapping.toDtoFullAcademyInfo)))
                )
              )
            } yield Behaviors.same[AcademyApiCommand]
          }.unsafeRunSync()
        case c @ GetAcademies(searchString, pagination) => {
            for {
              res <- AcademyOperations.getAcademies(searchString, pagination)
              _ <- IO(
                c.replyTo ! QueryServiceResponse().withGetAcademiesResponse(
                  query.GetAcademiesResponse().withAcademies(res._1.map(DtoMapping.toDtoFullAcademyInfo)).withPageInfo(
                    PageInfo().withPage(if (res._2.maxResults > 0) res._2.offset / res._2.maxResults else 0)
                      .withTotal(res._2.totalResults).withResultsOnPage(res._2.maxResults)
                  )
                )
              )
            } yield Behaviors.same[AcademyApiCommand]
          }.unsafeRunSync()
      }
    }
  }
}
