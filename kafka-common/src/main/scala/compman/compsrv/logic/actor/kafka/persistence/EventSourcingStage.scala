package compman.compsrv.logic.actor.kafka.persistence

import akka.kafka.ProducerMessage
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.NotUsed

case class EventSourcingStage[Command, Event, State, Error](
  initialState: State,
  operations: EventSourcingOperations[Command, Event, State, Error]
) extends GraphStage[FlowShape[ProducerMessage.Message[String, Command, NotUsed], Seq[Event]]] {

  type EventsWithPartitionOffset = Seq[Event]
  val in: Inlet[ProducerMessage.Message[String, Command, NotUsed]] =
    Inlet[ProducerMessage.Message[String, Command, NotUsed]]("EventSourcingStage.in")
  val out: Outlet[EventsWithPartitionOffset] = Outlet[EventsWithPartitionOffset]("EventSourcingStage.out")

  val shape: FlowShape[ProducerMessage.Message[String, Command, NotUsed], EventsWithPartitionOffset] = FlowShape
    .of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var state: State = initialState

    override def preStart(): Unit = {
      // a detached stage needs to start upstream demand
      // itself as it is not triggered by downstream demand
      pull(in)
    }

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val elem          = grab(in)
          val command       = elem.record.value()
          val eventsOrError = operations.processCommand(command, state)
          eventsOrError match {
            case Left(error) =>
              state = operations.processError(command, error, state)
              pull(in)
            case Right(events) =>
              state = events.foldLeft(state)((s, ev) => operations.applyEvent(ev, s))
              operations.optionallySaveStateSnapshot(state)
              emit(out, events)
          }
        }

        override def onUpstreamFinish(): Unit = { completeStage() }
      }
    )

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = { if (!hasBeenPulled(in)) { pull(in) } }
      }
    )
  }

}
