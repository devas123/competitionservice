package compman.compsrv.query.actors

import zio.Ref

case class Context[F[+_]](
  children: Ref[Map[String, ActorRef[Any]]],
  self: ActorRef[F],
  id: String,
  derivedSystem: ActorSystem
) {}
