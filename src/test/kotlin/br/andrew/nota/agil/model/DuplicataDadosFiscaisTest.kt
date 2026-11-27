package br.andrew.nota.agil.model

import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.qive.model.NotaProdutos
import br.andrew.nota.agil.qive.model.NotaServico
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Date

/** Dados ficticios: CNPJs, chaves e numeros nao correspondem a documentos reais. */
class DuplicataDadosFiscaisTest {

    private val mapper = jacksonObjectMapper()
    private val chave44 = "11261098765432000100550020000001231123456780"
    private val chave50 = "1100205" + "1" + "2" + "98765432000100" + "2600000001234" + "2611" + "123456789" + "0"

    private fun duplicata(tipo: TipoDuplicata, chaveAcesso: String?, serie: String? = null, chaveFiscal: String? = null) =
        Duplicata(Date(), "98765432000100", "P", "12345678000190", "T", BigDecimal.TEN, BigDecimal.TEN, "123", tipo,
            chaveAcesso, serie, chaveFiscal)

    @Test
    fun `NFe e CTe - chave de 44 e serie da chave (caracteres 23 a 25)`() {
        val nfe = duplicata(TipoDuplicata.Nfe, chave44, serie = "2")
        assertEquals(chave44, nfe.chaveAcessoFiscal())
        assertEquals("002", nfe.serieFiscal())
        val semChave = duplicata(TipoDuplicata.Cte, "abc", serie = "1")
        assertNull(semChave.chaveAcessoFiscal())
        assertEquals("1", semChave.serieFiscal())
    }

    @Test
    fun `NFS-e - chave fiscal e serie do RPS, nunca o id do Qive`() {
        val nfse = duplicata(TipoDuplicata.Nfse, "qive-id", serie = "00001", chaveFiscal = chave50)
        assertEquals(chave50, nfse.chaveAcessoFiscal())
        assertEquals("00001", nfse.serieFiscal())
        val antiga = duplicata(TipoDuplicata.Nfse, chave44)
        assertNull(antiga.chaveAcessoFiscal())
        assertNull(antiga.serieFiscal())
    }

    @Test
    fun `chave fiscal da NFS-e a partir do Id do Qive`() {
        assertEquals(chave50, Duplicata.chaveFiscalNfse("NFS$chave50"))
        assertEquals(chave50, Duplicata.chaveFiscalNfse(" nfs$chave50 "))
        assertEquals(chave50, Duplicata.chaveFiscalNfse(chave50))
        val dps = "DPS" + "1100205" + "2" + "98765432000100" + "00001" + "000000000000007"
        assertEquals(dps, Duplicata.chaveFiscalNfse(dps))
        assertNull(Duplicata.chaveFiscalNfse("NFS123"))
        assertNull(Duplicata.chaveFiscalNfse("Rps123"))
        assertNull(Duplicata.chaveFiscalNfse(null))
    }

    @Test
    fun `campos de serie e chave so vao ao SE com ID configurado`() {
        val nfe = duplicata(TipoDuplicata.Nfe, chave44)
        val padrao = nfe.getSoftexpertMap("fxpna").getValue("fxpna")
        assertEquals(10, padrao.size)
        assertFalse(padrao.values.contains(chave44))
        val com = nfe.getSoftexpertMap("fxpna", "seriedocfiscal", " chaveacesso ").getValue("fxpna")
        assertEquals("002", com["seriedocfiscal"])
        assertEquals(chave44, com["chaveacesso"])
        assertEquals("123", com["numdocfiscal"])
        val semValor = duplicata(TipoDuplicata.Nfse, "qive-id").getSoftexpertMap("fxpna", "seriedocfiscal", "chaveacesso")
        assertEquals(10, semValor.getValue("fxpna").size, "sem valor o campo nao vai (nem vazio)")
    }

    @Test
    fun `IDs de campo do SE nao podem repetir nem colidir com os campos fixos`() {
        WorkFlowEnvrioment("FXP-NA", "fxpna", "d", "a")
        WorkFlowEnvrioment("FXP-NA", "fxpna", "d", "a", "seriedocfiscal", "chaveacesso")
        assertThrows<IllegalArgumentException> { WorkFlowEnvrioment("FXP-NA", "fxpna", "d", "a", "x", "X") }
        assertThrows<IllegalArgumentException> { WorkFlowEnvrioment("FXP-NA", "fxpna", "d", "a", "numdocfiscal", null) }
    }

    @Test
    fun `Qive - serie do RPS e chave nacional da NFS-e, Rps vazio vira null`() {
        val nota: NotaServico = mapper.readValue("""
            {"id":"qive-id","xml":{"Nfse":{"InfNfse":{"@attributes":{"Id":"NFS$chave50"},"Numero":"2600000001234",
             "DeclaracaoPrestacaoServico":{"InfDeclaracaoPrestacaoServico":{"Rps":{"IdentificacaoRps":{"Numero":"7","Serie":" 00001 "}}}}}}}}
        """.trimIndent())
        assertEquals(chave50, nota.getChaveFiscal())
        assertEquals("00001", nota.getSerie())
        assertEquals("qive-id", nota.getChaveAcesso(), "id do Qive continua sendo o usado no download do PDF")
        val semRps: NotaServico = mapper.readValue("""
            {"id":"qive-id","xml":{"Nfse":{"InfNfse":{"@attributes":{"Id":"NFS123"},
             "DeclaracaoPrestacaoServico":{"InfDeclaracaoPrestacaoServico":{"Rps":[]}}}}}}
        """.trimIndent())
        assertNull(semRps.getSerie())
        assertNull(semRps.getChaveFiscal())
    }

    @Test
    fun `Qive - serie da NFe em ide`() {
        val nota: NotaProdutos = mapper.readValue("""{"access_key":"$chave44","xml":{"NFe":{"infNFe":{"ide":{"nNF":"123","serie":"2"}}}}}""")
        assertEquals("2", nota.getSerie())
    }

    @Test
    fun `pedido do SE aceita chaveacesso e chaveAcesso e normaliza os espacos`() {
        val base = """"workflowId":"W","solicitacaoId":"S","cpfcnpjtomador":"12345678000190","cnpjprestador":"98765432000100",
            "numdocfiscal":"1","valorparcela":10"""
        val a: RegistrarConsultaErpRequest = mapper.readValue("""{$base,"chaveacesso":" 1234 5678 "}""")
        val b: RegistrarConsultaErpRequest = mapper.readValue("""{$base,"chaveAcesso":"12345678"}""")
        assertEquals("12345678", a.normalizada().chaveAcesso)
        assertEquals(a.normalizada(), b.normalizada())
        val c: RegistrarConsultaErpRequest = mapper.readValue("""{$base,"chaveacesso":"  "}""")
        assertNull(c.normalizada().chaveAcesso)
    }
}
