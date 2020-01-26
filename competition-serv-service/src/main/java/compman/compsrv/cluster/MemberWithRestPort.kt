package compman.compsrv.cluster

import io.scalecube.cluster.Member
import io.scalecube.net.Address
import java.io.Serializable

data class MemberWithRestPort(val id: String, val host: String, val port: Int, val restPort: Int) : Serializable {
    constructor(member: Member, restPort: Int) : this(member.id(), member.address().host(), member.address().port(), restPort)

    fun restAddress(): Address= Address.create(host, restPort)
}