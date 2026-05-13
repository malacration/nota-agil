package br.andrew.nota.agil.qive.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QiveNfeReceivedParserTest {

    private val parser = QiveNfeReceivedParser(jacksonObjectMapper())

    @Test
    fun `deve manter body bruto ao falhar desserializacao`() {
        val body = """
            {
              "data": "estrutura inesperada",
              "page": null,
              "count": 1,
              "signatura": null,
              "status": {
                "code": 200,
                "message": "ok"
              }
            }
        """.trimIndent()

        val exception = assertFailsWith<QiveResponseBodyCaptureException> {
            parser.parseListReceived(
                body = body,
                endpoint = "/nfe/received",
                parametros = mapOf("cnpj" to "12345678000199"),
            )
        }

        assertEquals("/nfe/received", exception.endpoint)
        assertEquals(body, exception.responseBody)
        assertEquals("12345678000199", exception.parametros["cnpj"])
    }
}
