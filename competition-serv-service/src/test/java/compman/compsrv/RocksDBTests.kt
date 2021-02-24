package compman.compsrv

import compman.compsrv.aggregate.Category
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.repository.RocksDBProperties
import compman.compsrv.repository.RocksDBRepository
import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RocksDBTests {
    private val mapper = ObjectMapperFactory.createObjectMapper()

    @Test
    fun test() {
        // a static method that loads the RocksDB C++ library.
        RocksDB.loadLibrary()
        val options = Options().setCreateIfMissing(true)
        val testCompetitor = CompetitorDTO().setId("testId")
        val path = Files.createTempDirectory("testRocks")
        val db = RocksDB.open(options, path.toAbsolutePath().toString())
        db.use { d ->
            d.put("testId".toByteArray(), mapper.writeValueAsBytes(testCompetitor))
            val get = mapper.readValue(d.get("testId".toByteArray()), CompetitorDTO::class.java)
            assertTrue { get.id == "testId" }
        }
    }

    @Test
    fun test2() {
        val path = Files.createTempDirectory("testRocks")
        val rocksDbRepo = RocksDBRepository(mapper, RocksDBProperties().apply { this.path = path.toAbsolutePath().toString() })
        rocksDbRepo.doInTransaction { db ->
            db.putCategory(Category("test1", CategoryDescriptorDTO().setId("test1")))
            val cat = db.getCategory("test1")
            assertNotNull(cat)
        }
        rocksDbRepo.doInTransaction { db ->
            val cat = db.getCategories(listOf("test1"))
            assertNotNull(cat)
            assertEquals(1, cat.size)
        }
    }
}
