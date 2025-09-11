package br.andrew.nota.agil

import br.andrew.nota.agil.qive.interfaces.Page
import kotlin.test.Test
import kotlin.test.assertEquals

class PageTest {


    @Test
    fun test(){
        val page = Page(
            "https://api.arquivei.com.br/v1/nfe/received?created_at[from]=Mon Aug 25 13:00:00 AMT 2025&created_at[to]=Mon Aug 25 14:00:00 AMT 2025&cursor=7302&format_type=JSON&limit=50",
            "https://api.arquivei.com.br/v1/nfe/received?created_at[from]=Mon Aug 25 13:00:00 AMT 2025&created_at[to]=Mon Aug 25 14:00:00 AMT 2025&cursor=0&format_type=JSON&limit=50"
        )
        assertEquals(7302,page.getNextCursor())
        assertEquals(0,page.getPreviousCursor())
    }
}