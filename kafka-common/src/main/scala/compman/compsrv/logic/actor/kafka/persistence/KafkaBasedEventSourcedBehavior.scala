package compman.compsrv.logic.actor.kafka.persistence

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.Behavior
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Transactional
import akka.kafka.ConsumerMessage.{GroupTopicPartition, PartitionOffset, TransactionalMessage}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.NotUsed
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{CommandReceived, KafkaBasedEventSourcedBehaviorApi, Stop}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

abstract class KafkaBasedEventSourcedBehavior[State, KafkaCommand, KafkaEvent, Error](
  val competitionId: String,
  val eventsTopic: String,
  val commandClass: Class[KafkaCommand],
  context: ActorContext[KafkaBasedEventSourcedBehaviorApi]
) extends AbstractBehavior[KafkaBasedEventSourcedBehaviorApi](context) {
  def operations: EventSourcingOperations[KafkaCommand, KafkaEvent, State, Error]
  def getEvents(startFrom: Long): Seq[KafkaEvent]
  def getInitialState: State
  def getLatestOffset(state: State): Long
  def serializeEvent(event: KafkaEvent): Array[Byte]
  def commandSideEffect(msg: CommandReceived): Unit
  val transactionalId: String = competitionId
  val producerSettings: ProducerSettings[String, KafkaEvent] = ProducerSettings.create(
    context.system,
    new StringSerializer,
    new Serializer[KafkaEvent] {
      override def serialize(topic: String, data: KafkaEvent): Array[Byte] = serializeEvent(data)
    }
  )

  private implicit val materializer: Materializer = Materializer.createMaterializer(context.system)
  val updatedState: State = {
    val initialState: State = getInitialState
    getEvents(getLatestOffset(getInitialState)).foldLeft(initialState) { (s, e) => operations.applyEvent(e, s) }
  }
  val eventSourcingStage: EventSourcingStage[KafkaCommand, KafkaEvent, State, Error] =
    EventSourcingStage(updatedState, operations)
  val (sourceQueue, source) = Source
    .queue[TransactionalMessage[String, KafkaCommand]](10, OverflowStrategy.backpressure).preMaterialize()

  private val stream = source.via(eventSourcingStage).map { msg =>
    ProducerMessage.multi(msg._1.map(evt => new ProducerRecord(eventsTopic, competitionId, evt)), msg._2)
  }.via(producerFlow)

  protected def producerFlow: Flow[ProducerMessage.Envelope[String, KafkaEvent, PartitionOffset], ProducerMessage.Results[String, KafkaEvent, PartitionOffset], NotUsed] = {
    Transactional.flow(producerSettings, transactionalId)
  }

  stream.runWith(Sink.ignore)

  final override def onMessage(msg: KafkaBasedEventSourcedBehaviorApi): Behavior[KafkaBasedEventSourcedBehaviorApi] =
    msg match {
      case cmd @ CommandReceived(groupId, topic, competitionId, command, partition, offset) =>
        if (commandClass.isAssignableFrom(command.getClass)) {
          commandSideEffect(cmd)
          val trxMessage = TransactionalMessage(
            new ConsumerRecord[String, KafkaCommand](
              topic,
              partition,
              offset,
              competitionId,
              commandClass.cast(command)
            ),
            PartitionOffset(GroupTopicPartition(groupId, topic, partition), offset)
          )
          Await.result(sourceQueue.offer(trxMessage), 10.seconds)
        } else { context.log.warn(s"Wrong message: $command") }
        Behaviors.same[KafkaBasedEventSourcedBehaviorApi]
      case Stop => Behaviors.stopped[KafkaBasedEventSourcedBehaviorApi](() => sourceQueue.complete())
    }
}

object KafkaBasedEventSourcedBehavior {
  sealed trait KafkaBasedEventSourcedBehaviorApi
  case class CommandReceived(
    groupId: String,
    topic: String,
    competitionId: String,
    command: Any,
    partition: Int,
    offset: Long
  )                extends KafkaBasedEventSourcedBehaviorApi
  case object Stop extends KafkaBasedEventSourcedBehaviorApi
}
