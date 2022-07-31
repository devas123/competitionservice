package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.SubscribeToEnd

import java.util.UUID

object CommandCallbackListener {

  sealed trait CommandCallbackListenerApi
  case class KafkaMessageReceived(message: KafkaConsumerApi) extends CommandCallbackListenerApi
  case class RegisterListener(actorRef: ActorRef[KafkaConsumerApi], replyTo: ActorRef[RegisterListenerResponse])
      extends CommandCallbackListenerApi
  case class RemoveListener(id: String) extends CommandCallbackListenerApi
  type RegisterListenerResponse = Either[Throwable, String]

  private def updated(listeners: Map[String, ActorRef[KafkaConsumerApi]]): Behavior[CommandCallbackListenerApi] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case KafkaMessageReceived(message) =>
          listeners.values.foreach(_ ! message)
          Behaviors.same
        case RegisterListener(actorRef, replyTo) =>
          val id = UUID.randomUUID().toString
          ctx.watchWith(actorRef, RemoveListener(id))
          if (!listeners.contains(id)) {
            val newListeners = listeners + (id -> actorRef)
            replyTo ! Right(id)
            updated(newListeners)
          } else {
            replyTo ! Left(new Exception(s"Actor with $id already exists, try another id"))
            Behaviors.same
          }
        case RemoveListener(id) => updated(listeners - id)
      }
    }

  def behavior(
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    callbackTopic: String,
    groupId: String
  ): Behavior[CommandCallbackListenerApi] = Behaviors.setup { context =>
    val callbackReceiverAdapter = context.messageAdapter[KafkaConsumerApi](KafkaMessageReceived)
    kafkaSupervisor ! SubscribeToEnd(
      topic = callbackTopic,
      groupId = groupId,
      replyTo = callbackReceiverAdapter,
      commitOffsetToKafka = true
    )
    updated(Map.empty)
  }

}
