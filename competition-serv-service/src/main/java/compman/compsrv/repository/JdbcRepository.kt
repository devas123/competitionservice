package compman.compsrv.repository

import arrow.core.Tuple4
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Component
class JdbcRepository(private val jdbcTemplate: JdbcTemplate) {
    fun batchUpdateFightStartTimesMatPeriodNumber(newFights: List<FightStartTimePairDTO>) {
        jdbcTemplate.batchUpdate("UPDATE fight_description f SET start_time = ?, mat_id = ?, number_on_mat = ?, period = ? WHERE f.id = ?", object : BatchPreparedStatementSetter {
            override fun setValues(ps: PreparedStatement, i: Int) {
                if (i < newFights.size) {
                    val it = newFights[i]
                    ps.setTimestamp(1, Timestamp.from(it.startTime))
                    ps.setString(2, it.matId)
                    ps.setInt(3, it.fightNumber)
                    ps.setString(4, it.periodId)
                    ps.setString(5, it.fightId)
                }
            }
            override fun getBatchSize(): Int = newFights.size
        })
    }

    fun batchUpdateStartTimeAndMatAndNumberOnMatById(updates: List<Tuple4<String, Instant, String, Int>>) {
        jdbcTemplate.batchUpdate("UPDATE fight_description f SET start_time = ?, mat_id = ?, number_on_mat = ? WHERE f.id = ?", object : BatchPreparedStatementSetter {
            override fun setValues(ps: PreparedStatement, i: Int) {
                if (i < updates.size) {
                    val it = updates[i]
                    ps.setTimestamp(1, Timestamp.from(it.b))
                    ps.setString(2, it.c)
                    ps.setInt(3, it.d)
                    ps.setString(5, it.a)
                }
            }
            override fun getBatchSize(): Int = updates.size
        })
    }
}