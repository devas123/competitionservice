package compman.compsrv.logic.actors.behavior

import cats.effect.unsafe.IORuntime

trait WithIORuntime {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
}
