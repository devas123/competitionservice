package compman.compsrv.util

import com.google.common.hash.Hashing

object IDGenerator {
    fun hashString(str: String) = Hashing.sha256().hashBytes(str.toByteArray(Charsets.UTF_8)).toString()
}