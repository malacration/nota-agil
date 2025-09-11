package br.andrew.nota.agil.qive.interfaces

class Page(
    val next : String,
    val previous : String
){
    private fun extractCursor(url: String): Int {
        // Regex robusta que não depende de URL válida/encodada
        val regex = Regex("""(?:^|[?&])cursor=([^&#]+)""")
        val match = regex.find(url)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** Retorna o próximo cursor da fila. */
    fun getNextCursor(): Int {
        return extractCursor(next)
    }


    fun getPreviousCursor(): Int {
        return extractCursor(previous)
    }

    /** Não há próxima página quando não existe cursor seguinte. */
    fun isEnd(): Boolean {
        if (next.isBlank() || !next.contains("cursor=")) return true
        val nextCur = getNextCursor()
        if (nextCur == 0) return true
        val prevCur = getPreviousCursor()
        if (prevCur != 0 && prevCur == nextCur) return true
        return false
    }
}

