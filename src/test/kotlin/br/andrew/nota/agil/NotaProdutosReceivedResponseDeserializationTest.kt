package br.andrew.nota.agil

import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaProdutos
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotaProdutosReceivedResponseDeserializationTest {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun readFixture(path: String): String =
        checkNotNull(javaClass.getResource(path)) { "Fixture nao encontrada: $path" }
            .readText()

    @Test
    fun `deve desserializar quando vol vem como array no json da NFe`() {
        val json = """
            {
              "status": {
                "code": 200,
                "message": "Ok"
              },
              "data": [
                {
                  "access_key": "11260561064929011103550010000023351655267713",
                  "xml": {
                    "@attributes": {
                      "versao": "4.00"
                    },
                    "NFe": {
                      "infNFe": {
                        "@attributes": {
                          "Id": "NFe11260561064929011103550010000023351655267713",
                          "versao": "4.00"
                        },
                        "ide": {
                          "cUF": "11",
                          "cNF": "65526771",
                          "natOp": "VENDA MERC.ADQ.RECEB.DE TERCEIROS",
                          "mod": "55",
                          "serie": "1",
                          "nNF": "2335",
                          "dhEmi": "2026-05-06T13:35:56-04:00",
                          "tpNF": "1",
                          "idDest": "1",
                          "cMunFG": "1100304",
                          "tpImp": "1",
                          "tpEmis": "1",
                          "cDV": "3",
                          "tpAmb": "1",
                          "finNFe": "6",
                          "indFinal": "0",
                          "indPres": "9",
                          "procEmi": "0",
                          "verProc": "SAP GRC NFE 10.0"
                        },
                        "emit": {
                          "CNPJ": "61064929011103",
                          "xNome": "CORTEVA AGRISCIENCE DO BRASIL LTDA."
                        },
                        "dest": {
                          "CNPJ": "05925052000435",
                          "xNome": "FAZENDA RIO MADEIRA S A-FARM"
                        },
                        "det": {
                          "@attributes": {
                            "nItem": "1"
                          },
                          "prod": {
                            "cProd": "P3707VYH-B821",
                            "xProd": "SEMENTE DE MILHO HÍBRIDO",
                            "vProd": "39600.82"
                          },
                          "imposto": {
                            "IBSCBS": {
                              "CST": "515"
                            }
                          }
                        },
                        "total": {
                          "ICMSTot": {
                            "vProd": "0.00",
                            "vNF": "0.00"
                          }
                        },
                        "transp": {
                          "modFrete": "0",
                          "vol": []
                        },
                        "pag": {
                          "detPag": {
                            "tPag": "90",
                            "vPag": "0.00"
                          }
                        },
                        "infAdic": {
                          "infCpl": "NOTA FISCAL DE DÉBITO"
                        }
                      }
                    },
                    "protNFe": {
                      "@attributes": {
                        "versao": "4.00"
                      },
                      "infProt": {
                        "tpAmb": "1",
                        "verAplic": "SVRS2604010855DR",
                        "chNFe": "11260561064929011103550010000023351655267713",
                        "dhRecbto": "2026-05-06T13:36:07-04:00",
                        "nProt": "211260012396200",
                        "digVal": "0xg45mW94ytPuSM/XIrqaFuBrnM=",
                        "cStat": "100",
                        "xMotivo": "Autorizado o uso da NF-e"
                      }
                    }
                  }
                }
              ],
              "page": {
                "next": "next",
                "previous": "previous"
              },
              "count": 1,
              "signature": "bef8e6b2468970a8acf96e0c4f1f97dbe936dbef1103220f1d526d4af89cb4dd"
            }
        """.trimIndent()

        val response = mapper.readValue(json, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})
        val nota = response.data.single()

        assertEquals("11260561064929011103550010000023351655267713", nota.id)
        assertNotNull(nota.xml?.nfe?.infNFe?.transp?.vol)
        assertTrue(nota.xml?.nfe?.infNFe?.transp?.vol?.isEmpty() == true)
        assertEquals(1, nota.xml?.nfe?.infNFe?.det?.size)
        assertEquals(1, nota.xml?.nfe?.infNFe?.pag?.size)
        assertEquals(1, nota.xml?.nfe?.infNFe?.pag?.first()?.detPag?.size)
    }

    @Test
    fun `deve transformar objeto unico em lista nos campos de array da NFe`() {
        val json = """
            {
              "status": {
                "code": 200,
                "message": "Ok"
              },
              "data": [
                {
                  "access_key": "41260577300861000110550010000172011133642124",
                  "xml": {
                    "@attributes": {
                      "versao": "4.00"
                    },
                    "NFe": {
                      "infNFe": {
                        "@attributes": {
                          "Id": "NFe41260577300861000110550010000172011133642124",
                          "versao": "4.00"
                        },
                        "ide": {
                          "nNF": "17201",
                          "dhEmi": "2026-05-06T09:18:00-03:00"
                        },
                        "emit": {
                          "CNPJ": "77300861000110",
                          "xNome": "FABRICA DE TRONCOS ROMANCINI LTDA"
                        },
                        "dest": {
                          "CNPJ": "05925052000435",
                          "xNome": "FAZENDA RIO MADEIRA S/A"
                        },
                        "det": {
                          "@attributes": {
                            "nItem": "1"
                          },
                          "prod": {
                            "cProd": "68010.0001",
                            "xProd": "Trava Hidraulica Vertical Laranja",
                            "vProd": "1900.00"
                          }
                        },
                        "total": {
                          "ICMSTot": {
                            "vProd": "1900.00",
                            "vNF": "2161.75"
                          }
                        },
                        "transp": {
                          "modFrete": "0",
                          "vol": {
                            "qVol": "1",
                            "pesoL": "12.000",
                            "pesoB": "12.000"
                          }
                        },
                        "cobr": {
                          "dup": {
                            "nDup": "001",
                            "dVenc": "2026-06-05",
                            "vDup": "1080.88"
                          }
                        },
                        "pag": {
                          "detPag": {
                            "tPag": "99",
                            "vPag": "1080.88"
                          }
                        },
                        "infAdic": {
                          "infCpl": "teste"
                        }
                      }
                    }
                  }
                }
              ],
              "page": {
                "next": "next",
                "previous": "previous"
              },
              "count": 1,
              "signature": "abc"
            }
        """.trimIndent()

        val response = mapper.readValue(json, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})
        val infNFe = response.data.single().xml?.nfe?.infNFe

        assertEquals(1, infNFe?.det?.size)
        assertEquals(1, infNFe?.transp?.vol?.size)
        assertEquals("1", infNFe?.transp?.vol?.first()?.qVol)
        assertEquals(1, infNFe?.cobr?.dup?.size)
        assertEquals(1, infNFe?.pag?.size)
        assertEquals(1, infNFe?.pag?.first()?.detPag?.size)
    }

    @Test
    fun `deve desserializar resposta completa da Qive a partir de fixture`() {
        val json = readFixture("/fixtures/nfe-received-response.json")

        val response = mapper.readValue(json, object : TypeReference<ReceivedResponse<NotaProdutos>>() {})

        assertEquals(200, response.status.code)
        assertEquals("Ok", response.status.message)
        assertEquals(14, response.count)
        assertEquals(14, response.data.size)

        val notaComVolVazio = response.data.first { it.id == "11260561064929011103550010000023351655267713" }
        assertNotNull(notaComVolVazio.xml?.nfe?.infNFe?.transp?.vol)
        assertTrue(notaComVolVazio.xml?.nfe?.infNFe?.transp?.vol?.isEmpty() == true)

        val notaComVolObjeto = response.data.first { it.id == "41260577300861000110550010000172011133642124" }
        assertEquals(1, notaComVolObjeto.xml?.nfe?.infNFe?.transp?.vol?.size)
        assertEquals("1", notaComVolObjeto.xml?.nfe?.infNFe?.transp?.vol?.first()?.qVol)

        val notaComMultiplosItens = response.data.first { it.id == "51260547180625003595550050000688491905034735" }
        assertEquals(2, notaComMultiplosItens.xml?.nfe?.infNFe?.det?.size)

        val notaComDuplicataUnica = response.data.first { it.id == "11260506982796000101550010000368201043770293" }
        assertEquals(1, notaComDuplicataUnica.xml?.nfe?.infNFe?.cobr?.dup?.size)

        val notaComPagamentoUnico = response.data.first { it.id == "11260521571964000160550020001445301450138448" }
        assertEquals(1, notaComPagamentoUnico.xml?.nfe?.infNFe?.pag?.size)
        assertEquals(1, notaComPagamentoUnico.xml?.nfe?.infNFe?.pag?.first()?.detPag?.size)

        assertFalse(response.data.isEmpty())
    }
}
