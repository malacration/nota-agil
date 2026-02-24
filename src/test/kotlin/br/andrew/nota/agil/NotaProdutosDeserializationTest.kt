package br.andrew.nota.agil

import br.andrew.nota.agil.qive.model.InfNFe
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals

class NotaProdutosDeserializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `deve desserializar infAdic como objeto`() {
        val infNFe: InfNFe = mapper.readValue(
            """
            {
              "infAdic": {
                "infCpl": "informacao complementar"
              }
            }
            """.trimIndent()
        )

        assertEquals("informacao complementar", infNFe.infAdic?.infCpl)
    }

    @Test
    fun `deve desserializar infAdic como array`() {
        val infNFe: InfNFe = mapper.readValue(
            """
            {
              "infAdic": [
                {
                  "infCpl": "informacao complementar"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("informacao complementar", infNFe.infAdic?.infCpl)
    }
}
