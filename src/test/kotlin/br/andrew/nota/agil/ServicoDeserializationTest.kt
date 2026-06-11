package br.andrew.nota.agil

import br.andrew.nota.agil.qive.model.NotaServico
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServicoDeserializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `deve ignorar campos municipais nao utilizados`() {
        val nota: NotaServico = mapper.readValue(
            """
            {
              "id": "nfse-123",
              "xml": {
                "Nfse": {
                  "InfNfse": {
                    "@attributes": {"Id": "NFS123"},
                    "Numero": "123",
                    "CodigoVerificacao": "ABC123",
                    "DataEmissao": "2026-05-06T09:18:00-04:00",
                    "ValoresNfse": {
                      "ValorLiquidoNfse": "95.00",
                      "OutrasInformacoes": []
                    },
                    "PrestadorServico": {
                      "IdentificacaoPrestador": {
                        "CpfCnpj": {"Cnpj": "10922863000170"}
                      },
                      "RazaoSocial": "PRESTADOR LTDA",
                      "Endereco": [],
                      "Contato": []
                    },
                    "DeclaracaoPrestacaoServico": {
                      "InfDeclaracaoPrestacaoServico": {
                        "@attributes": {"Id": "DEC123"},
                        "Rps": [],
                        "Servico": {
                          "Valores": {"ValorServicos": "100.00"},
                          "ItemListaServico": ["14.01", "14.02"],
                          "Discriminacao": []
                        },
                        "Tomador": {
                          "IdentificacaoTomador": {
                            "CpfCnpj": {"Cnpj": "05884660000104"}
                          },
                          "RazaoSocial": "TOMADOR LTDA",
                          "Endereco": []
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val duplicata = nota.getDuplicata()

        assertEquals("123", duplicata.numero)
        assertEquals("10922863000170", duplicata.cpfCnpjPrestador)
        assertEquals("05884660000104", duplicata.cpfCnpjTomador)
        assertEquals("100.00", duplicata.valorBruto.toPlainString())
        assertEquals("95.00", duplicata.valorLiquido.toPlainString())
        assertNotNull(nota.getDataEmissao())
    }
}
