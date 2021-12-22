package compman.compsrv.logic.actors.patterns

import compman.compsrv.logic.actors.{ActorRef, MinimalActorRef}
import zio.{Promise, Task, ZIO}

import scala.language.implicitConversions

object Patterns {

  private[actors] class AskActorRef[F](actorRef: ActorRef[F]) {
    def ?[OutMsg](fa: ActorRef[OutMsg] => F): Task[OutMsg] = for {
      promise      <- Promise.make[Throwable, OutMsg]
      replyToActor <- ZIO.effectTotal(PromiseActorRef(promise))
      msg          <- ZIO.effectTotal(fa(replyToActor))
      _            <- actorRef ! msg
      response     <- promise.await
    } yield response
  }

  private[actors] case class PromiseActorRef[Msg](promise: Promise[Throwable, Msg]) extends MinimalActorRef[Msg] {
    override def !(fa: Msg): Task[Unit] = promise.succeed(fa).unit
    override private[actors] val stop                                  = promise.fail(new RuntimeException("Stopped before getting the result")) *> Task(List.empty)
  }

  implicit def withAskSupport[F](actorRef: ActorRef[F]): AskActorRef[F] = new AskActorRef[F](actorRef)

  private[actors] object PromiseActorRef {}
}
