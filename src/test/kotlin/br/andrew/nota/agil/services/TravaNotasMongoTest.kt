package br.andrew.nota.agil.services

import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** Trava no Mongo simulada: `_id` unico em memoria, remove pelos mesmos criterios da query. */
class TravaNotasMongoTest {
    private val docs = ConcurrentHashMap<String, Document>()
    private val mongo = mock(MongoTemplate::class.java).also { m ->
        `when`(m.insert(any(Document::class.java), eq(TravaNotasMongo.COLECAO))).thenAnswer {
            val d = it.getArgument<Document>(0)
            if (docs.putIfAbsent(d.getString("_id"), d) != null) throw DuplicateKeyException("E11000")
            d
        }
        `when`(m.remove(any(Query::class.java), eq(TravaNotasMongo.COLECAO))).thenAnswer {
            val q = it.getArgument<Query>(0).queryObject
            val id = q.getString("_id")
            val atual = docs[id]
            val dono = q["dono"] as? String
            val antesDe = (q["criadaEm"] as? Document)?.get("\$lt") as? Date
            if (atual != null && (dono == null || atual.getString("dono") == dono) &&
                (antesDe == null || atual.getDate("criadaEm").before(antesDe))) docs.remove(id)
            null
        }
    }
    private val trava = TravaNotasMongo(mongo).apply { espera = Duration.ofMillis(200) }

    @Test
    fun `executa o bloco com a trava e libera no fim, mesmo com erro`() {
        assertEquals(42, trava.comTrava("k") { assertTrue(docs.containsKey("k")); 42 })
        assertTrue(docs.isEmpty())
        assertThrows<IllegalArgumentException> { trava.comTrava("k") { throw IllegalArgumentException("x") } }
        assertTrue(docs.isEmpty())
    }

    @Test
    fun `trava ocupada por outro espera e desiste e trava vencida e retomada`() {
        docs["k"] = Document(mapOf("_id" to "k", "dono" to "outro", "criadaEm" to Date.from(Instant.now())))
        assertThrows<IllegalStateException> { trava.comTrava("k") { 1 } }
        assertEquals("outro", docs["k"]?.getString("dono"), "nao remove a trava valida de outro dono")

        docs["k"] = Document(mapOf("_id" to "k", "dono" to "outro", "criadaEm" to Date.from(Instant.now().minus(Duration.ofMinutes(5)))))
        assertEquals(1, trava.comTrava("k") { 1 })
        assertTrue(docs.isEmpty())
    }
}
