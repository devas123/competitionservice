package compman.compsrv.logic.actors

import compman.compsrv.model.events.EventDTO
import zio.{Has, Layer, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.kafka.producer.Producer

trait CommandProcessorConfig {
  def kafkaConsumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]]
  def kafkaProducerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]]
  def clockLayer: Layer[Nothing, Clock]
  def blockingLayer: Layer[Nothing, Blocking]
}
