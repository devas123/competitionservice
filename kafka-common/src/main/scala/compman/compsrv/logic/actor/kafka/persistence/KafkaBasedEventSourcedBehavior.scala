package compman.compsrv.logic.actor.kafka.persistence

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.Behavior
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Producer
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.NotUsed
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{CommandReceived, KafkaBasedEventSourcedBehaviorApi, KafkaProducerFlow, Stop}
import org.apache.kafka.clients.producer.ProducerRecord

abstract class KafkaBasedEventSourcedBehavior[State, KafkaCommand, KafkaEvent, Error](
  val competitionId: String,
  val eventsTopic: String,
  val commandClass: Class[KafkaCommand],
  val producerSettings: ProducerSettings[String, Array[Byte]],
  context: ActorContext[KafkaBasedEventSourcedBehaviorApi]
) extends AbstractBehavior[KafkaBasedEventSourcedBehaviorApi](context) {
  def operations: EventSourcingOperations[KafkaCommand, KafkaEvent, State, Error]
  def getEvents(startFrom: Long): Seq[KafkaEvent]
  def getInitialState: State
  def getLatestOffset(state: State): Long
  def serializeEvent(event: KafkaEvent): Array[Byte]
  def commandSideEffect(msg: CommandReceived): Unit

  private implicit val materializer: Materializer = Materializer.createMaterializer(context.system)
  val updatedState: State = {
    val initialState: State = getInitialState
    getEvents(getLatestOffset(getInitialState)).foldLeft(initialState) { (s, e) => operations.applyEvent(e, s) }
  }
  val eventSourcingStage: EventSourcingStage[KafkaCommand, KafkaEvent, State, Error] =
    EventSourcingStage(updatedState, operations)
  val (sourceQueue, source) = Source
    .queue[ProducerMessage.Message[String, KafkaCommand, NotUsed]](10, OverflowStrategy.backpressure).preMaterialize()

  private val defaultProducer: KafkaProducerFlow = Producer.flexiFlow(producerSettings)
  protected def producerFlow: KafkaProducerFlow  = defaultProducer

  private val producer = producerFlow

  source.via(eventSourcingStage).map { msg =>
    ProducerMessage.multi(msg.map(evt => new ProducerRecord(eventsTopic, competitionId, serializeEvent(evt))))
  }.via(producer).runWith(Sink.ignore)

  final override def onMessage(msg: KafkaBasedEventSourcedBehaviorApi): Behavior[KafkaBasedEventSourcedBehaviorApi] =
    msg match {
      case cmd @ CommandReceived(topic, competitionId, command, partition) =>
        context.log.info(s"Received command: $cmd")
        if (commandClass.isAssignableFrom(command.getClass)) {
          commandSideEffect(cmd)
          val producerMessage = ProducerMessage.Message(
            new ProducerRecord[String, KafkaCommand](topic, partition, competitionId, commandClass.cast(command)),
            NotUsed
          )
          sourceQueue.offer(producerMessage)
        } else { context.log.warn(s"Wrong message: $command") }
        Behaviors.same[KafkaBasedEventSourcedBehaviorApi]
      case Stop => Behaviors.stopped[KafkaBasedEventSourcedBehaviorApi](() => sourceQueue.complete())
    }
}

object KafkaBasedEventSourcedBehavior {
  type KafkaProducerFlow = Flow[
    ProducerMessage.Envelope[String, Array[Byte], NotUsed],
    ProducerMessage.Results[String, Array[Byte], NotUsed],
    NotUsed
  ]
  sealed trait KafkaBasedEventSourcedBehaviorApi
  case class CommandReceived(topic: String, competitionId: String, command: Any, partition: Int)
      extends KafkaBasedEventSourcedBehaviorApi
  case object Stop extends KafkaBasedEventSourcedBehaviorApi
}
