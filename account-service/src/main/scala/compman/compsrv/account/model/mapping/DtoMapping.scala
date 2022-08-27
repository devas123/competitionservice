package compman.compsrv.account.model.mapping

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.account.model.InternalAccount
import compservice.model.protobuf.account.Account

import java.util.Date

object DtoMapping {
  def toInternalAccount(dto: Account): InternalAccount = {
    InternalAccount(dto.userId, dto.firstName, dto.lastName, dto.email, dto.birthDate.map(ts => ts.asJavaInstant))
  }
  def toDtoAccount(o: InternalAccount): Account =
    Account.defaultInstance
      .update(
        _.userId := o.userId,
        _.firstName := o.firstName,
        _.lastName := o.lastName,
        _.email := o.email,
        _.birthDate.setIfDefined(o.birthDate.map(i => Timestamp.fromJavaProto(Timestamps.fromDate(Date.from(i)))))
      )
}
