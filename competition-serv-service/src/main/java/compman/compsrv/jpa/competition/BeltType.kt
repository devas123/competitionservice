package compman.compsrv.jpa.competition

object BeltType {
    const val WHITE = "WHITE"
    const val GRAY = "GRAY"
    const val YELLOW = "YELLOW"
    const val ORANGE = "ORANGE"
    const val GREEN = "GREEN"
    const val BLUE = "BLUE"
    const val PURPLE = "PURPLE"
    const val BROWN = "BROWN"
    const val BLACK = "BLACK"

    fun getOrdinal(belt: String): Int {
        return when (belt) {
            WHITE -> 0
            GRAY -> 10
            YELLOW -> 20
            GREEN -> 30
            BLUE -> 40
            PURPLE -> 50
            BROWN -> 60
            BLACK -> 70
            else -> 100
        }
    }

    fun values(age: String?) = if (age == null) emptyArray() else {
        if (age.equals("ADULT", ignoreCase = true) || age.startsWith("MASTER", ignoreCase = true)) arrayOf(WHITE,
                BLUE,
                PURPLE,
                BROWN,
                BLACK)
        else if (age.startsWith("JUVENILE", ignoreCase = true)) {
            arrayOf(WHITE,
                    BLUE)

        } else {
            arrayOf(WHITE,
                    GRAY,
                    YELLOW,
                    GREEN)
        }
    }
}