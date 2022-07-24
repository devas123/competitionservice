package compman.compsrv.jackson

import compman.compsrv.SpecBase
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.commandpayload.CreateCompetitionPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.model.{CompetitionProperties, RegistrationInfo}

class SerdeApiTest extends SpecBase {
  test("Command serializer should deserialize command") {
    val competitionId = "CompetitionId"
    val command = Command().withType(CommandType.CREATE_COMPETITION_COMMAND).withMessageInfo(
      MessageInfo().withCompetitionId(competitionId).withPayload(MessageInfo.Payload.CreateCompetitionPayload(
        CreateCompetitionPayload().withReginfo(RegistrationInfo().withId(competitionId))
          .withProperties(CompetitionProperties().withId(competitionId))
      ))
    )
    val bytes = command.toByteArray
    val deser = Command.parseFrom(bytes)
    assert(deser.messageInfo.map(_.payload).isDefined)
    assert(deser.messageInfo.map(_.payload).exists(_.isCreateCompetitionPayload))
    assert(deser.messageInfo.map(_.payload).exists(p =>
      p.createCompetitionPayload.flatMap(_.properties).exists(_.id == competitionId)
    ))
  }
}
