package compman.compsrv.repository

import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
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

    fun deleteFightStartTimesCategoryId(categoryId: String?) {
        if (!categoryId.isNullOrBlank()) {
            jdbcTemplate.update("DELETE from fight_start_times fs using fight_description fd where fs.fight_id = fd.id and fd.category_id = ?") { ps ->
                ps.setString(1, categoryId)
            }
        }
    }

    fun getFightBasicInfo(id: String): Optional<OnlyStageId> {
        val res = jdbcTemplate.query("select f.stage_id, f.win_fight, f.lose_fight from fight_description f where f.id = ?",
                PreparedStatementSetter { ps -> ps.setString(1, id) },
                ResultSetExtractor<Optional<OnlyStageId>> { resultSet: ResultSet ->
                    if (resultSet.next()) {
                        Optional.of(object : OnlyStageId {
                            private val stageId: String = resultSet.getString(1)
                            private val winFight: String? = resultSet.getString(2)
                            private val loseFight: String? = resultSet.getString(3)
                            override fun getStageId(): String? = stageId
                            override fun getWinFight(): String? = winFight
                            override fun getLoseFight(): String? = loseFight
                        })
                    } else {
                        Optional.empty()
                    }
                })
        return res ?: Optional.empty()
    }
}