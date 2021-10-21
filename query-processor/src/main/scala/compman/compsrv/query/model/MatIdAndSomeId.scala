package compman.compsrv.query.model

import io.getquill.Udt

import java.util.Date

case class MatIdAndSomeId(matId: String, someId: String, startTime: Option[Date]) extends Udt
