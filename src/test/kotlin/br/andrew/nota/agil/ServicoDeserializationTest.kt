package br.andrew.nota.agil

import br.andrew.nota.agil.qive.model.Servico
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals

class ServicoDeserializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `deve desserializar ItemListaServico como string`() {
        val servico: Servico = mapper.readValue(
            """
            {
              "ItemListaServico": "14.01"
            }
            """.trimIndent()
        )

        assertEquals("14.01", servico.itemListaServico)
    }

    @Test
    fun `deve desserializar ItemListaServico como array`() {
        val servico: Servico = mapper.readValue(
            """
            {
              "ItemListaServico": ["14.01", "14.02"]
            }
            """.trimIndent()
        )

        assertEquals("14.01", servico.itemListaServico)
    }
}
