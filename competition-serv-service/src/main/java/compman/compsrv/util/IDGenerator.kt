package compman.compsrv.util

import com.google.common.hash.Hashing

object IDGenerator {
    private const val SALT = "zhenekpenek"
    fun hashString(str: String) = Hashing.sha256().hashBytes("$SALT$str".toByteArray(Charsets.UTF_8)).toString()
}