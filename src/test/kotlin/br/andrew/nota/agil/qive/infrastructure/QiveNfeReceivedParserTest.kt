package br.andrew.nota.agil.qive.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QiveNfeReceivedParserTest {

    private val parser = QiveNfeReceivedParser(jacksonObjectMapper())

    @Test
    fun `deve ignorar transportadora enviada como array vazio`() {
        val body = """
            {
              "data": [
                {
                  "access_key": "35260310922863000170550050000095081645995259",
                  "xml": {
                    "NFe": {
                      "infNFe": {
                        "ide": {
                          "nNF": "9508",
                          "dhEmi": "2026-03-02T21:17:59-03:00"
                        },
                        "emit": {
                          "CNPJ": "10922863000170",
                          "xNome": "EMITENTE"
                        },
                        "dest": {
                          "CNPJ": "05884660000104",
                          "xNome": "DESTINATARIO"
                        },
                        "total": {
                          "ICMSTot": {
                            "vProd": "181.70",
                            "vNF": "181.70"
                          }
                        },
                        "transp": {
                          "modFrete": "2",
                          "transporta": []
                        }
                      }
                    }
                  }
                }
              ],
              "page": null,
              "count": 1,
              "signatura": null,
              "status": {
                "code": 200,
                "message": "ok"
              }
            }
        """.trimIndent()

        val response = parser.parseListReceived(
            body = body,
            endpoint = "/nfe/received",
            parametros = mapOf("cnpj" to "05884660000104"),
        )

        assertEquals("9508", response.data.single().getNumero())
    }

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
