package br.andrew.nota.agil

import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaProdutos
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NotaProdutosReceivedResponseDeserializationTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun readFixture(path: String): String =
        checkNotNull(javaClass.getResource(path)) { "Fixture nao encontrada: $path" }
            .readText()

    @Test
    fun `deve ignorar campos nao utilizados com formatos variaveis`() {
        val json = """
            {
              "status": {"code": 200, "message": "Ok"},
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
                          "xNome": "VIA PRISMA OTICA DE FRANCA LTDA"
                        },
                        "dest": {
                          "CNPJ": "05884660000104",
                          "xNome": "UZZIPAY ADMINISTRADORA DE CONVENIOS LTDA"
                        },
                        "det": [{"prod": {"cBenef": []}}],
                        "total": {
                          "ICMSTot": {
                            "vProd": "181.70",
                            "vNF": "181.70"
                          },
                          "IBSCBSTot": {"gIBS": []}
                        },
                        "transp": {
                          "modFrete": "2",
                          "transporta": [],
                          "vol": []
                        },
                        "pag": {"detPag": [{"card": []}]},
                        "infAdic": []
                      },
                      "Signature": []
                    }
                  }
                }
              ],
              "page": null,
              "count": 1,
              "signature": "abc"
            }
        """.trimIndent()

        val response = mapper.readValue(json, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})
        val nota = response.data.single()
        val duplicata = nota.getDuplicata()

        assertEquals("9508", nota.getNumero())
        assertEquals("10922863000170", nota.getCpfCnpjEmitente())
        assertEquals("181.70", duplicata.valorBruto.toPlainString())
        assertEquals("181.70", duplicata.valorLiquido.toPlainString())
    }

    @Test
    fun `deve desserializar resposta completa da Qive a partir de fixture`() {
        val json = readFixture("/fixtures/nfe-received-response.json")

        val response = mapper.readValue(json, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})

        assertEquals(200, response.status.code)
        assertEquals("Ok", response.status.message)
        assertEquals(14, response.count)
        assertEquals(14, response.data.size)

        val nota = response.data.first {
            it.id == "11260561064929011103550010000023351655267713"
        }
        assertEquals("2335", nota.getNumero())
        assertEquals("61064929011103", nota.getCpfCnpjEmitente())
        assertNotNull(nota.getDataEmissao())
        assertEquals("0.00", nota.getValorLiquido()?.toPlainString())
    }
}
