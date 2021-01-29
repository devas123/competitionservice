package compman.compsrv.repository

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import org.rocksdb.*
import org.springframework.util.FileSystemUtils
import java.nio.file.Files
import java.nio.file.Path

class RocksDBRepository(private val mapper: ObjectMapper, dbProperties: RocksDBProperties) {
    private val db: OptimisticTransactionDB
    private val options: Options
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
    }

    init {
        if (Files.exists(Path.of(path))) {
            FileSystemUtils.deleteRecursively(Path.of(path))
        }
        OptimisticTransactionDB.loadLibrary()
        options = Options().setCreateIfMissing(true)
        Files.createDirectories(Path.of(path))
        db = OptimisticTransactionDB.open(options, path)
        val columnFamilyHandles = ColumnFamilyOptions().optimizeUniversalStyleCompaction().use { opts ->
            db.createColumnFamilies(
                listOf(
                    CATEGORY,
                    COMPETITION,
                    SCHEDULE,
                    COMPETITOR
                ).map { ColumnFamilyDescriptor(it.toByteArray(), opts) })
        }
        categories = columnFamilyHandles[0]
        competitions = columnFamilyHandles[1]
        schedules = columnFamilyHandles[2]
        competitors = columnFamilyHandles[3]
        operations = RocksDBOperations(db.right(), mapper, competitors, competitions, categories)
    }

    fun shutdown() {
        FileSystemUtils.deleteRecursively(Path.of(path))
    }

    fun <T> doInTransaction(snapshot: Boolean = false, retries: Int = 3, block: (tx: RocksDBOperations) -> T): T {
        fun exec(tx: Transaction): T {
            val result = block(RocksDBOperations(tx.left(), mapper, competitors, competitions, categories))
            tx.commit()
            return result
        }

        var i = 0
        var exception: RocksDBException? = null
        val writeOptions = WriteOptions()
        val txOptions = OptimisticTransactionOptions().setSetSnapshot(snapshot)
        val tx = db.beginTransaction(writeOptions, txOptions)
        tx.use { transaction ->
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

        throw exception ?: RocksDBException("Transaction failed.")
    }

    fun getOperations() = operations

}