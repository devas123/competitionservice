package compman.compsrv.query.model

import io.getquill.Udt

import java.time.Instant

case class MatIdAndSomeId(matId: String, someId: String, startTime: Option[Instant]) extends Udt
