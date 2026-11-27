package br.andrew.nota.agil.erp.notas

import br.andrew.nota.agil.erp.dealer.DealerleniumClient
import br.andrew.nota.agil.erp.dealer.DealerleniumProperties
import br.andrew.nota.agil.erp.sap.SapContasPagarProperties
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.TipoDuplicata
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Dados ficticios: codigos, CNPJs, credenciais e valores nao correspondem a registros reais. */
class DealerNotasEntradaErpTest {

    private val base = "http://dealerlenium.exemplo.invalid"
    private val tokenUrl = "https://sso.exemplo.invalid/token"
    private val properties = DealerleniumProperties().apply { url = base }
    private val sap = SapContasPagarProperties().apply {
        this.tokenUrl = this@DealerNotasEntradaErpTest.tokenUrl; clientId = "client-teste"; clientSecret = "segredo-teste"
    }
    private val flags = NotasErpProperties()
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = DealerleniumClient(properties, sap, builder.build(), Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC))
    private val dealer = DealerNotasEntradaErp(client, flags)
    private val empresa = Company("12345678000190", "Empresa X", codigoDealer = 7)

    private fun token(valor: String = "tok-1", vezes: ExpectedCount = ExpectedCount.once()) {
        server.expect(vezes, requestTo(tokenUrl)).andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("client_id=client-teste")))
            .andRespond(withSuccess("""{"access_token":"$valor","expires_in":300}""", MediaType.APPLICATION_JSON))
    }

    private val notas = """{"notas":[
        {"id":"nf900","numero":123,"dataEmissao":"2026-11-03","dataLancamento":"2026-11-05","nomeFornecedor":"Fornecedor X",
         "cpfCnpjFornecedor":"98765432000100","valorTotal":100.10,
         "parcelas":[{"tituloCodigo":1,"valor":60.00},{"tituloCodigo":2,"valor":40.10}],"problema":null},
        {"id":"t4","numero":null,"parcelas":[{"tituloCodigo":4}],"problema":"Titulo sem nota fiscal vinculada no Dealer"}]}"""

    @Test
    fun `lista pelo dealerlenium com token da service account e converte para o modelo comum`() {
        token()
        server.expect(requestTo("$base/api/contas-pagar/notas-entrada/listagem")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer tok-1"))
            .andExpect(content().string(containsString("\"empresaCodigo\":7")))
            .andExpect(content().string(containsString("\"dataInicial\":\"2026-11-01\"")))
            .andRespond(withSuccess(notas, MediaType.APPLICATION_JSON))

        val lista = dealer.listar(empresa, FiltroNotasErp("2026-11-01", "2026-11-30")).associateBy { it.id }

        val nf = lista.getValue("nf900")
        assertEquals(123L, nf.numero)
        assertEquals(BigDecimal("100.10"), nf.valorBruto)
        assertEquals(2, nf.parcelas)
        assertEquals(TipoDuplicata.NaoDetectado, nf.tipo)
        assertNull(nf.problema)
        assertEquals("Titulo sem nota fiscal vinculada no Dealer", lista.getValue("t4").problema)
        server.verify()
    }

    @Test
    fun `busca por ids valida o formato antes de chamar o dealerlenium`() {
        assertThrows<ResponseStatusException> { dealer.buscar(empresa, listOf("x' OR 1=1")) }
        token()
        server.expect(requestTo("$base/api/contas-pagar/notas-entrada/listagem"))
            .andExpect(content().string(containsString("\"ids\":[\"nf900\"]")))
            .andRespond(withSuccess(notas, MediaType.APPLICATION_JSON))
        assertEquals(setOf("nf900", "t4"), dealer.buscar(empresa, listOf("nf900")).map { it.id }.toSet())
        server.verify()
    }

    @Test
    fun `401 renova o token uma vez e 403 vira 502 sem repassar detalhes`() {
        token("tok-1")
        server.expect(requestTo("$base/api/contas-pagar/empresas")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        token("tok-2")
        server.expect(requestTo("$base/api/contas-pagar/empresas")).andExpect(header("Authorization", "Bearer tok-2"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))
        val erro = assertThrows<ResponseStatusException> { dealer.filiais() }
        assertEquals(HttpStatus.BAD_GATEWAY, erro.statusCode)
        server.verify()
    }

    @Test
    fun `filiais do Dealer vem das empresas com codigo do dealerlenium`() {
        token()
        server.expect(requestTo("$base/api/contas-pagar/empresas")).andRespond(withSuccess(
            """[{"codigoDealer":1,"cnpj":"12.345.678/0001-90","nome":"Loja A"},{"codigoDealer":2,"cnpj":"123","nome":"Invalida"}]""",
            MediaType.APPLICATION_JSON))
        assertEquals(listOf(FilialErp("12345678000190", "Loja A", codigoDealer = 1)), dealer.filiais())
    }

    @Test
    fun `erro 400 do dealerlenium volta como 400 com a mensagem`() {
        token()
        server.expect(requestTo("$base/api/contas-pagar/notas-entrada/listagem"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("""{"status":400,"message":"Periodo maximo de 31 dias"}"""))
        val erro = assertThrows<ResponseStatusException> { dealer.listar(empresa, FiltroNotasErp("2026-10-01", "2026-11-30")) }
        assertEquals(HttpStatus.BAD_REQUEST, erro.statusCode)
        assertEquals("Periodo maximo de 31 dias", erro.reason)
    }

    @Test
    fun `situacao reflete flag, URL e credenciais (proprias ou do SAP)`() {
        assertTrue(dealer.situacao().disponivel)
        flags.dealer.enabled = false
        assertFalse(dealer.situacao().disponivel)
        flags.dealer.enabled = true
        sap.clientSecret = ""
        assertEquals("Variaveis de conexao ausentes", dealer.situacao().motivo)
        properties.tokenUrl = tokenUrl; properties.clientId = "outro"; properties.clientSecret = "outro-segredo"
        assertTrue(dealer.situacao().disponivel)
        properties.url = ""
        assertFalse(dealer.situacao().disponivel)
        assertThrows<ResponseStatusException> { dealer.listar(empresa, FiltroNotasErp("2026-11-01", "2026-11-30")) }
    }

    @Test
    fun `empresa sem codigo Dealer e indisponivel`() {
        assertEquals("Empresa sem codigo no Dealer (cadastre em Empresas)", dealer.motivoEmpresaIndisponivel(Company("1", "x")))
        assertNull(dealer.motivoEmpresaIndisponivel(empresa))
    }

    @Test
    fun `Keycloak recusando a service account vira mensagem especifica, sem chamar o dealerlenium`() {
        server.expect(requestTo(tokenUrl)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_JSON).body("""{"error":"unauthorized_client"}"""))
        val erro = assertThrows<ResponseStatusException> { dealer.filiais() }
        assertEquals(HttpStatus.BAD_GATEWAY, erro.statusCode)
        assertEquals(br.andrew.nota.agil.erp.FalhaTokenServiceAccount.MENSAGEM, erro.reason)
        server.verify()
    }
}
