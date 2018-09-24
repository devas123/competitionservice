package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.kafka.streams.JobStream
import compman.compsrv.model.competition.MatState
import compman.compsrv.service.CategoryStateService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.kafka.streams.state.HostInfo
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock

class WorkerProcess(private val zk: CuratorFramework,
                    private val znode: String,
                    private val closingListener: ClosingListener,
                    private val kafkaProperties: KafkaProperties,
                    private val competitionStateService: CategoryStateService,
                    private val hostInfo: HostInfo,
                    private val zookeeperSession: ZookeeperSession,
                    private val validators: CategoryCommandsValidatorRegistry,
                    private val matCommandsValidatorRegistry: MatCommandsValidatorRegistry) : Watcher {

    companion object {
        private val log = LoggerFactory.getLogger(WorkerProcess::class.java)
    }

    private val job: JobStream = createJob()

    @Volatile
    private var dead = false

    private val lock = ReentrantLock()

    override fun process(event: WatchedEvent?) {
        if (!dead && zk.state == CuratorFrameworkState.STARTED) {
            event?.also {
                when (it.type) {
                    Watcher.Event.EventType.None ->
                        if (it.state == Watcher.Event.KeeperState.Expired) {
                            closingListener.workerClosing(KeeperException.Code.SESSIONEXPIRED)
                        }
                    else ->
                        log.debug("Event for some other znode, skipping.")

                }
            }
            zk.checkExists().usingWatcher(this@WorkerProcess).forPath(znode)
        } else {
            log.warn("Got an event to process but either i am dead ($dead), or ZK session is not STARTED (${zk.state})")
        }

    }


    fun start() {
        if (!dead) {
            lock.lock()
            try {
                job.start()
            } finally {
                lock.unlock()
            }
        }
    }

    fun stop() {
        if (!dead) {
            lock.lock()
            try {
                dead = true
                try {
                    job.stop()
                } catch (e: Exception) {
                    log.error("Exception while stopping worker thread.", e)
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private fun createJob() = JobStream(kafkaProperties, competitionStateService, hostInfo, zookeeperSession, validators, matCommandsValidatorRegistry)

    fun getMetadataService() = job.metadataService

    fun getCategoryState(categoryUid: String) = job.getCategoryState(categoryUid)
    fun getMatState(matId: String): MatState? = job.getMatState(matId)
}