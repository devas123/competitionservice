package compman.compsrv.jackson

import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.model.{CompetitionProperties, RegistrationInfo}
import zio.Task
import zio.test._

object SerdeApiTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Command serializer")(testM("Command serializer should deserialize command") {
      for {
        competitionId <- Task("CompetitionId")
        command <- Task {
          Command().withType(CommandType.CREATE_COMPETITION_COMMAND).withMessageInfo(
            MessageInfo().withCompetitionId(competitionId).withPayload(MessageInfo.Payload.CreateCompetitionPayload(
              CreateCompetitionPayload().withReginfo(RegistrationInfo().withId(competitionId))
                .withProperties(CompetitionProperties().withId(competitionId))
            ))
          )
        }
        bytes = command.toByteArray
        _ <- Task(println(new String(bytes)))
        deser = Command.parseFrom(bytes)

      } yield assertTrue(deser.messageInfo.map(_.payload).isDefined) &&
        assertTrue(deser.messageInfo.map(_.payload).exists(_.isCreateCompetitionPayload)) &&
        assertTrue(deser.messageInfo.map(_.payload).exists(p =>
          p.createCompetitionPayload.flatMap(_.properties).exists(_.id == competitionId)
        ))
    })
}
