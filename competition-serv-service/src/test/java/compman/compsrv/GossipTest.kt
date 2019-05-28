package compman.compsrv

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.cluster.CompetitionProcessingInfo
import compman.compsrv.cluster.CompetitionProcessingMessage
import compman.compsrv.cluster.MemberWithRestPort
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterConfig
import io.scalecube.transport.Message
import org.junit.Test
import org.slf4j.LoggerFactory

class GossipTest {

    companion object {
        private val log = LoggerFactory.getLogger(GossipTest::class.java)
    }

    @Test
    fun testGossip() {
        val mapper = ObjectMapper().findAndRegisterModules()
        val cluster1 = Cluster.joinAwait()
        val cluster2 = Cluster.joinAwait(ClusterConfig.builder().seedMembers(cluster1.address()).build())

        cluster2.listenGossips().subscribe { t: Message? ->
            log.warn("$t")
        }

        val message = Message.builder()
                .data(mapper.writeValueAsString(CompetitionProcessingMessage(MemberWithRestPort(cluster1.member(), 100500), CompetitionProcessingInfo(MemberWithRestPort(cluster1.member(), 100500), setOf("a","b", "c")))))
                .header(ClusterSession.TYPE, ClusterSession.COMPETITION_PROCESSING_STARTED)
                .sender(cluster1.member().address())
                .build()



        cluster1.spreadGossip(message).subscribe()
        cluster1.spreadGossip(message).subscribe()
        cluster1.spreadGossip(message).subscribe()
        cluster2.spreadGossip(message).subscribe()
        cluster2.spreadGossip(message).subscribe()

        Thread.sleep(1000)
    }

}