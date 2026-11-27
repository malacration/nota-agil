package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Clock

/**
 * Com `NOTA_AGIL_SECURITY_DISABLED` (teste local/homolog) a consulta ao sap-rovema dispensa as
 * credenciais de service account. Dados ficticios.
 */
@ExtendWith(OutputCaptureExtension::class)
class SapSemTokenSegurancaDesativadaTest {

    @Configuration
    @EnableConfigurationProperties
    @Import(SapContasPagarProperties::class, SapRovemaTokenProvider::class, SapConsultaErpClient::class)
    class Beans

    private val runner = ApplicationContextRunner().withUserConfiguration(Beans::class.java)
        .withPropertyValues("integracao.erp.sap.url=https://sap-rovema.example.invalid")

    @Test
    fun `so com URL e sem a chave o SAP continua desabilitado`() {
        runner.run { ctx -> assertFalse(ctx.getBean(SapConsultaErpClient::class.java).consultaPagamentoImplementada) }
    }

    @Test
    fun `com a chave e so a URL o SAP fica habilitado sem token`() {
        runner.withPropertyValues("NOTA_AGIL_SECURITY_DISABLED=true").run { ctx ->
            assertTrue(ctx.getBean(SapRovemaTokenProvider::class.java).semToken)
            assertTrue(ctx.getBean(SapConsultaErpClient::class.java).consultaPagamentoImplementada)
        }
    }

    @Test
    fun `com a chave mas sem URL continua desabilitado`() {
        ApplicationContextRunner().withUserConfiguration(Beans::class.java)
            .withPropertyValues("nota-agil.seguranca.desativada=true").run { ctx ->
                assertFalse(ctx.getBean(SapConsultaErpClient::class.java).consultaPagamentoImplementada)
            }
    }

    @Test
    fun `sem token avisa na subida com WARN so com os nomes das credenciais ausentes`(output: CapturedOutput) {
        val properties = SapContasPagarProperties().apply {
            url = "https://sap-rovema.example.invalid"
            clientId = "nota-agil-teste"
            clientSecret = "segredo-de-teste"
        }
        SapRovemaTokenProvider(properties, Clock.systemUTC(), RestClient.create(), true)
        assertTrue(output.out.contains("consultas ao sap-rovema vao SEM Authorization"), output.out)
        assertTrue(output.out.contains("credenciais ausentes: integracao.erp.sap.tokenUrl)"), output.out)
        assertFalse(output.out.contains("segredo-de-teste"))
    }

    @Test
    fun `com token nenhum WARN de chamada sem token`(output: CapturedOutput) {
        val properties = SapContasPagarProperties().apply {
            url = "https://sap-rovema.example.invalid"
            tokenUrl = "https://sso.example.invalid/token"
            clientId = "nota-agil-teste"
            clientSecret = "segredo-de-teste"
        }
        SapRovemaTokenProvider(properties, Clock.systemUTC(), RestClient.create(), true)
        SapRovemaTokenProvider(SapContasPagarProperties().apply { url = properties.url }, Clock.systemUTC(),
            RestClient.create(), false)
        assertFalse(output.out.contains("SEM Authorization"), output.out)
    }

    @Test
    fun `com a chave e credenciais configuradas o token continua sendo usado`() {
        val properties = SapContasPagarProperties().apply {
            url = "https://sap-rovema.example.invalid"
            tokenUrl = "https://sso.example.invalid/token"
            clientId = "nota-agil-teste"
            clientSecret = "segredo-de-teste"
        }
        assertFalse(SapRovemaTokenProvider(properties, Clock.systemUTC(), RestClient.create(), true).semToken)
    }

    @Test
    fun `consulta vai sem Authorization e sem chamar o SSO`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val rest = builder.build()
        val properties = SapContasPagarProperties().apply { url = "https://sap-rovema.example.invalid" }
        val tokens = SapRovemaTokenProvider(properties, Clock.systemUTC(), rest, true)
        val client = SapConsultaErpClient(properties, tokens, rest)
        server.expect(requestTo("https://sap-rovema.example.invalid${SapConsultaErpClient.CAMINHO}"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess("""{"codigo":"EM_ABERTO","mensagem":"Parcela em aberto"}""", MediaType.APPLICATION_JSON))
        val r = client.consultar(RegistrarConsultaErpRequest(
            workflowId = "FXP-NA000001", solicitacaoId = "baixa-1",
            cpfCnpjTomador = "12345678000190", cnpjPrestador = "98765432000100",
            numDocFiscal = "000123", valorParcela = BigDecimal("10.00"), serieDocFiscal = "001",
            tipoDoc = TipoDuplicata.Nfse, numeroParcela = "1",
        ))
        server.verify()
        assertEquals("EM_ABERTO", r.codigo)
    }
}
