package compman.compsrv.account.model.mapping

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.account.model.InternalAccount
import compservice.model.protobuf.account.Account
import io.github.nremond.SecureHash

import java.util.Date

object DtoMapping {
  def toInternalAccount(dto: Account, id: String): InternalAccount = {
    InternalAccount(id, dto.firstName, dto.lastName, dto.email, dto.birthDate.map(ts => ts.asJavaInstant), SecureHash.createHash(dto.password))
  }
  def toDtoAccount(o: InternalAccount): Account =
    Account.defaultInstance
      .update(
        _.userId := o.userId,
        _.firstName := o.firstName,
        _.lastName := o.lastName,
        _.email := o.email,
        _.birthDate.setIfDefined(o.birthDate.map(i => Timestamp.fromJavaProto(Timestamps.fromDate(Date.from(i))))),
        _.password := o.password
      )
}
