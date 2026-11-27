package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.tasks.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.mongodb.core.MongoTemplate

class IndiceIdentidadeNotaTest {
    @Test
    fun `indice e unico e esparso so no campo novo`() {
        val indice = IndiceIdentidadeNota.definicao()
        assertEquals(org.bson.Document("identidadeNota", 1), indice.indexKeys)
        assertEquals(true, indice.indexOptions["unique"])
        assertEquals(true, indice.indexOptions["sparse"])
        assertEquals(IndiceIdentidadeNota.NOME, indice.indexOptions["name"])
    }

    @Test
    fun `falha ao criar o indice impede a subida da aplicacao`() {
        val mongo = mock(MongoTemplate::class.java)
        `when`(mongo.indexOps(Task::class.java)).thenThrow(RuntimeException("sem Mongo"))
        val erro = assertThrows<IllegalStateException> { IndiceIdentidadeNota(mongo).criar() }
        assertTrue(erro.message!!.contains(IndiceIdentidadeNota.NOME))
    }
}
