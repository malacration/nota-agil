package br.andrew.nota.agil.softexpert

import br.andrew.nota.agil.infrastructure.SoapConfig
import br.andrew.nota.agil.infrastructure.SoftExpertEnvrioment
import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.qive.interfaces.DocumentPdfData
import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.nota.agil.softexpert.infrastructure.ApiException
import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConfiguration
import br.andrew.nota.agil.softexpert.infrastructure.SoftexpertWebClientConfig
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftexpertApiClient
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.Workflow
import br.andrew.nota.agil.softexpert.service.DocumentService
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date

/**
 * Teste de integracao contra o ambiente de TESTE do SoftExpert (rovema-test.softexpert.app).
 * Grava dados reais la: instancia fluxo, cria documento, associa e executa atividade.
 *
 * Usa os valores de `application-hmg.properties`, trocando apenas a URL/host para o ambiente de teste.
 * Qualquer valor pode ser sobrescrito por variavel de ambiente (relaxed binding do Spring):
 *   SOFTEXPERT_IT_URL              -> URL do ambiente (padrao https://rovema-test.softexpert.app)
 *   SOFTEXPERT_API_AUTHORIZATION   -> JWT do API Gateway (REST)
 *   SOFTEXPERT_API_CDUSER          -> cdUser usado na listagem REST
 *   SOFT_EXPERT_USER / SOFT_EXPERT_PASSWORD -> credenciais SOAP
 *   SOFTEXPERT_WORKFLOW_IDPROCESS, SOFTEXPERT_WORKFLOW_TABLENAME,
 *   SOFTEXPERT_WORKFLOW_DOCCATEGORY, SOFTEXPERT_WORKFLOW_IDATIVIDADEUPLOAD
 *
 * Execucao (so roda com SOFTEXPERT_IT=true, para nao bater no ambiente externo no build):
 *   SOFTEXPERT_IT=true ./gradlew test --tests '*SoftExpertIntegrationTest'
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SOFTEXPERT_IT", matches = "true")
@SpringBootTest(
    classes = [
        SoftExpertIntegrationTest.Config::class,
        SoftExpertEnvrioment::class,
        SoapConfig::class,
        SoftexpertWebClientConfig::class,
        WorkFlowService::class,
        DocumentService::class,
    ],
    properties = [
        "softexpert.api.url=\${SOFTEXPERT_IT_URL:https://rovema-test.softexpert.app}",
        "soft.expert.host=\${SOFTEXPERT_IT_URL:https://rovema-test.softexpert.app}",
    ],
)
@ActiveProfiles("hmg")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SoftExpertIntegrationTest {

    @TestConfiguration
    @EnableConfigurationProperties(WorkFlowEnvrioment::class)
    class Config

    @Autowired lateinit var api: SoftexpertApiClient
    @Autowired lateinit var props: SoftExpertConfiguration
    @Autowired lateinit var flow: WorkFlowEnvrioment
    @Autowired lateinit var workFlowService: WorkFlowService
    @Autowired lateinit var documentService: DocumentService

    private val execucao = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
    private lateinit var workflowId: String
    private lateinit var documentoId: String

    // ---------- REST (API Gateway /apigateway/se/exp/chatbot/api) ----------

    @Test
    @Order(1)
    fun `deve listar instancias de workflow pelo REST`() {
        val cdUser = props.cdUser ?: error("softexpert.api.cdUser nao configurado")

        val resultado = api.workflow.search(cdUser, qtLimit = 5).getOrThrow()

        assertTrue(resultado.total >= resultado.results.size)
        resultado.results.forEach {
            assertFalse(it.oid.isNullOrBlank(), "instancia sem oid: ${it.identifier}")
        }
    }

    @Test
    @Order(2)
    fun `deve recusar chamada REST com token invalido`() {
        val workflowSemToken = HttpServiceProxyFactory.builder()
            .exchangeAdapter(RestClientAdapter.create(
                RestClient.builder()
                    .baseUrl(props.url)
                    .defaultHeaders { h ->
                        h.accept = listOf(MediaType.APPLICATION_JSON)
                        h.add("Authorization", "token-invalido")
                    }
                    .defaultStatusHandler({ it.isError }) { _, res ->
                        throw ApiException(res.statusCode.value(), res.body.bufferedReader().readText())
                    }
                    .build(),
            ))
            .build()
            .createClient(Workflow::class.java)

        val erro = assertThrows<ApiException> { workflowSemToken.search(props.cdUser ?: "0") }

        assertEquals(401, erro.status)
    }

    // ---------- SOAP (wf_ws.php / dc_ws.php) ----------

    @Test
    @Order(3)
    fun `deve instanciar fluxo via SOAP`() {
        val duplicata = Duplicata(
            dataEmissao = Date(),
            cpfCnpjPrestador = "00000000000191",
            razaoPrestador = "PRESTADOR TESTE INTEGRACAO",
            cpfCnpjTomador = "00000000000272",
            razaoTomador = "TOMADOR TESTE INTEGRACAO",
            valorBruto = BigDecimal("123.45"),
            valorLiquido = BigDecimal("120.00"),
            numero = execucao,
            tipo = TipoDuplicata.Nfe,
        )

        val resposta = workFlowService.instanciaFluxo(
            flow.idProcess,
            "[IT] ${duplicata.getTitulo()}",
            duplicata.getSoftexpertMap(flow.tableName),
        )

        assertEquals("SUCCESS", resposta.status, resposta.detail)
        assertFalse(resposta.recordID.isNullOrBlank())
        workflowId = resposta.recordID
        println("Workflow criado: recordID=${resposta.recordID} recordKey=${resposta.recordKey}")
    }

    @Test
    @Order(4)
    fun `deve falhar ao instanciar processo inexistente`() {
        val erro = assertThrows<Exception> {
            workFlowService.instanciaFluxo("PROCESSO-INEXISTENTE-IT", "[IT] processo invalido $execucao")
        }

        assertTrue(erro.message!!.startsWith("Erro SE"), erro.message)
    }

    @Test
    @Order(5)
    fun `deve gravar documento PDF via SOAP`() {
        val pdf = DocumentPdfResponse(DocumentPdfData("it-$execucao", Base64.getEncoder().encodeToString(PDF_MINIMO)))

        val resposta = documentService.save(pdf, "IT documento $execucao", flow.docCategory)

        assertNotNull(resposta)
        assertFalse(resposta!!.recordID.isNullOrBlank(), resposta.detail)
        documentoId = resposta.recordID
        println("Documento criado: recordID=${resposta.recordID}")
    }

    @Test
    @Order(6)
    fun `deve associar documento a atividade do fluxo`() {
        assumeTrue(::workflowId.isInitialized && ::documentoId.isInitialized, "depende da criacao do fluxo e do documento")

        val resposta = workFlowService.associa(flow.idAtividadeUpload, workflowId, documentoId)

        assertEquals("SUCCESS", resposta.status, resposta.detail)
    }

    @Test
    @Order(7)
    fun `deve executar atividade de upload do fluxo`() {
        assumeTrue(::workflowId.isInitialized, "depende da criacao do fluxo")

        val resposta = workFlowService.executaAtividade(workflowId, flow.idAtividadeUpload)

        assertEquals("SUCCESS", resposta.status) {
            "${resposta.detail} | atividade enviada=${flow.idAtividadeUpload} | " +
                "situacao atual da instancia=${situacaoAtual(workflowId)}"
        }
    }

    /** instanceStatus do REST e a situacao da instancia ("<idSituacao> - <nome>"), nao o ID da atividade. */
    private fun situacaoAtual(identificador: String): String = runCatching {
        api.workflow.search(props.cdUser ?: "0", qtLimit = 50).getOrThrow()
            .results.firstOrNull { it.identifier == identificador || it.oid == identificador }
            ?.instanceStatus ?: "instancia $identificador nao aparece na listagem do cdUser=${props.cdUser}"
    }.getOrElse { "erro ao consultar REST: ${it.message}" }

    companion object {
        private val PDF_MINIMO = """
            %PDF-1.4
            1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
            2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
            3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj
            trailer<</Root 1 0 R>>
            %%EOF
        """.trimIndent().toByteArray()
    }
}
