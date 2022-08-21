package compman.compsrv.query.actors.behavior

import cats.effect.unsafe.IORuntime

trait WithIORuntime {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
}
