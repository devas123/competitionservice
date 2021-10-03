package compman.compsrv.jackson

import compman.compsrv.model.commands.{CommandDTO, CommandType}
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, RegistrationInfoDTO}
import zio.{Task, ZIO}
import zio.test._
import zio.test.Assertion._

object SerdeApiTest extends DefaultRunnableSpec {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Command serializer")(testM("Command serializer should deserialize command") {
      for {
        mapper <- ZIO.effectTotal(ObjectMapperFactory.createObjectMapper)
        competitionId = "CompetitionId"
        command <- Task {
          val cmd = new CommandDTO()
          cmd.setType(CommandType.CREATE_COMPETITION_COMMAND)
          cmd.setCompetitionId(competitionId)
          cmd.setPayload(
            new CreateCompetitionPayload().setReginfo(new RegistrationInfoDTO().setId(competitionId))
              .setProperties(new CompetitionPropertiesDTO().setId(competitionId))
          )
          cmd
        }
        bytes = mapper.writeValueAsBytes(command)
        _ <- Task(println(new String(bytes)))
        deser = mapper.readValue(bytes, classOf[CommandDTO])

      } yield assert(deser.getPayload != null)(isTrue) &&
        assert(deser.getPayload.isInstanceOf[CreateCompetitionPayload])(isTrue) &&
        assert(deser.getPayload.asInstanceOf[CreateCompetitionPayload].getProperties.getId == competitionId)(isTrue)
    })
}
