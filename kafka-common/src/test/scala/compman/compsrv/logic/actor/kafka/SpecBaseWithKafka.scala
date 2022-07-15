package compman.compsrv.logic.actor.kafka

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.kafka.testkit.scaladsl.ScalatestKafkaSpec
import org.scalatest.{BeforeAndAfter, CancelAfterFailure}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class SpecBaseWithKafka(kafkaPort: Int)
    extends ScalatestKafkaSpec(kafkaPort)
    with AnyFunSuiteLike
    with Matchers
    with ScalaFutures
    with Eventually
    with BeforeAndAfter
    with CancelAfterFailure {

  protected val actorTestKit: ActorTestKit = ActorTestKit()
  def waitForTopic(notificationTopic: String): Unit = {
    var topicCreated = false
    var retries      = 10000
    while (!topicCreated && retries > 0) {
      log.info(s"Waiting for the topic $notificationTopic to be created")
      val topics = adminClient.listTopics().names().get().asScala
      topicCreated = topics.toSet.contains(notificationTopic)
      retries -= 1
    }
  }
  protected def this() = this(kafkaPort = -1)
}
