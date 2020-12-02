package compman.compsrv

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.dto.competition.CompetitorDTO
import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RocksDBTests {

    @Test
    fun test() {
        // a static method that loads the RocksDB C++ library.
        RocksDB.loadLibrary()
        val options = Options().setCreateIfMissing(true)
        val path = "C:/tmp/testRocks/db"
        val testCompetitor = CompetitorDTO().setId("testId")
        val mapper = ObjectMapper()
        Files.createDirectories(Path.of(path))
        val db = RocksDB.open(options, path)
        db.use { d ->
            d.put("testId".toByteArray(), mapper.writeValueAsBytes(testCompetitor))
            val get = mapper.readValue(d.get("testId".toByteArray()), CompetitorDTO::class.java)
            assertTrue { get.id == "testId" }
        }
        deleteDirectory(Path.of(path))
    }

    private fun deleteDirectory(directoryToBeDeleted: Path) {
        if (Files.isDirectory(directoryToBeDeleted)) {
            Files.list(directoryToBeDeleted)
                    .forEach(::deleteDirectory)
        }
        Files.delete(directoryToBeDeleted)
    }
}
