package compman.compsrv.repository

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.rocksdb.*
import org.slf4j.LoggerFactory
import org.springframework.util.FileSystemUtils
import java.nio.file.Files
import java.nio.file.Path

class RocksDBRepository(private val mapper: ObjectMapper, dbProperties: RocksDBProperties) {
    private val db: OptimisticTransactionDB
    private val options: DBOptions
    private val opts: ColumnFamilyOptions
    private val path = dbProperties.path
    private val competitors: ColumnFamilyHandle
    private val competitions: ColumnFamilyHandle
    private val categories: ColumnFamilyHandle
    private val schedules: ColumnFamilyHandle
    private val operations: RocksDBOperations

    companion object {
        const val CATEGORY = "category"
        const val COMPETITION = "competition"
        const val COMPETITOR = "competitor"
        const val SCHEDULE = "schedule"
        private val log = LoggerFactory.getLogger(RocksDBRepository::class.java)
    }

    init {
        if (Files.exists(Path.of(path))) {
            FileSystemUtils.deleteRecursively(Path.of(path))
        }
        OptimisticTransactionDB.loadLibrary()
        options = DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
        Files.createDirectories(Path.of(path))
        opts = ColumnFamilyOptions().optimizeUniversalStyleCompaction()
        val columnFamilyHandles = mutableListOf<ColumnFamilyHandle>()
        val columnFamilyDescriptors = listOf(
            RocksDB.DEFAULT_COLUMN_FAMILY,
            CATEGORY.toByteArray(),
            COMPETITION.toByteArray(),
            SCHEDULE.toByteArray(),
            COMPETITOR.toByteArray()
        ).map { ColumnFamilyDescriptor(it, opts) }
        db = OptimisticTransactionDB.open(options, path, columnFamilyDescriptors, columnFamilyHandles)
        categories = columnFamilyHandles[1]
        competitions = columnFamilyHandles[2]
        schedules = columnFamilyHandles[3]
        competitors = columnFamilyHandles[4]
        operations = RocksDBOperations(db.right(), mapper, competitors, competitions, categories)
    }

    fun shutdown() {
        competitors.close()
        competitions.close()
        schedules.close()
        categories.close()
        opts.close()
        options.close()
    }

    fun <T> doInTransaction(snapshot: Boolean = false, retries: Int = 3, block: (tx: RocksDBOperations) -> T): T {
        fun exec(tx: Transaction): T {
            val result = block(RocksDBOperations(tx.left(), mapper, competitors, competitions, categories))
            log.info("committing transaction")
            tx.commit()
            log.info("committing transaction.Done.")
            return result
        }

        var i = 0
        var exception: RocksDBException? = null
        WriteOptions().use { writeOptions ->
            OptimisticTransactionOptions().setSetSnapshot(snapshot).use { txOptions ->
                db.beginTransaction(writeOptions, txOptions).use { transaction ->
                    while (i < retries) {
                        try {
                            return exec(transaction)
                        } catch (e: RocksDBException) {
                            exception = e
                            if (e.status?.code == Status.Code.TryAgain) {
                                i++
                            } else {
                                throw e
                            }
                        }
                    }
                }
            }
        }
        throw exception ?: RocksDBException("Transaction failed.")
    }
    fun getOperations() = operations
}