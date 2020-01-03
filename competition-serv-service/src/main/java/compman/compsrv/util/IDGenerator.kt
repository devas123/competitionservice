package compman.compsrv.util

import com.google.common.hash.Hashing
import java.util.*
import kotlin.random.Random
import kotlin.random.nextULong

object IDGenerator {
    private const val SALT = "zhenekpenek"
    private val random = Random(System.currentTimeMillis())
    fun uid() = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    fun luid() = random.nextLong()
    fun hashString(str: String) = Hashing.sha256().hashBytes("$SALT$str".toByteArray(Charsets.UTF_8)).toString()
}