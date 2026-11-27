package br.andrew.nota.agil.softexpert

import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConsultaParser
import br.andrew.nota.agil.softexpert.infrastructure.SoftexpertWebClientConfig
import br.andrew.nota.agil.softexpert.service.SoftExpertConsultaService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException

class SoftExpertConsultaHttpTest {
    private val builder = RestClient.builder().baseUrl("https://se.example.invalid")
        .defaultHeader("Authorization", "token-de-teste")
        .defaultHeader("Accept", "application/json")
        .defaultHeader("Content-Type", "application/json")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val config = SoftexpertWebClientConfig()
    private val rest = builder.build()
    private val consulta = SoftExpertConsultaService(config.softexpertConsultaSoap(rest), SoftExpertConsultaParser())

    @Test
    fun `consulta SOAP deve usar gateway token headers XML e matricula correta`() {
        server.expect(requestTo("https://se.example.invalid/apigateway/se/ws/adm_ws.php"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "token-de-teste"))
            .andExpect(header("SOAPAction", "urn:admin#getUserData"))
            .andExpect(header("Accept", "text/xml"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
            .andExpect(xpath("/s:Envelope/s:Body/a:getUserData/a:iduser", mapOf(
                "s" to "http://schemas.xmlsoap.org/soap/envelope/", "a" to "urn:admin",
            )).string("001"))
            .andRespond(withSuccess(SoftExpertConsultaParserTest.USER_SOAP, MediaType.parseMediaType("text/xml; charset=UTF-8")))
        assertEquals("001", consulta.usuario(" 001 ").matricula)
        server.verify()
    }

    @Test
    fun `consulta de instancia deve enviar SOAPAction e ler WorkflowTitle real`() {
        server.expect(requestTo("https://se.example.invalid/apigateway/se/ws/wf_ws.php"))
            .andExpect(header("SOAPAction", "urn:workflow#getWorkflow"))
            .andRespond(withSuccess(SoftExpertConsultaParserTest.soap("urn:workflow", "getWorkflowResponse",
                "<Status>SUCCESS</Status><WorkflowTitle>Nota</WorkflowTitle><StarterUser>001</StarterUser>"), MediaType.TEXT_XML))
        assertEquals("Nota", consulta.instancia("WF001").title)
        server.verify()
    }

    @Test
    fun `consulta REST deve enviar cdUser e desserializar instancias`() {
        server.expect(requestTo("https://se.example.invalid/apigateway/se/exp/chatbot/api/instance.php?cdUser=2&cdProduct=39&fgLanguage=2&fgStatus=1&qtLimit=2"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "token-de-teste"))
            .andRespond(withSuccess("""{"success":true,"response":{"total":1,"results":[{"oid":"123","identifier":"WF001","fgStatus":1}]}}""", MediaType.APPLICATION_JSON))
        val resultado = config.softexpertApi(rest).workflow.search("2", fgLanguage = 2, fgStatus = 1, qtLimit = 2).getOrThrow()
        assertEquals("WF001", resultado.results.single().identifier)
        server.verify()
    }

    @Test
    fun `identificadores vazios devem ser rejeitados sem HTTP`() {
        assertThrows<ResponseStatusException> { consulta.usuario(" ") }
        assertThrows<ResponseStatusException> { consulta.instancia(" ") }
        assertThrows<ResponseStatusException> { consulta.usuario("a".repeat(101)) }
        server.verify()
    }
}
