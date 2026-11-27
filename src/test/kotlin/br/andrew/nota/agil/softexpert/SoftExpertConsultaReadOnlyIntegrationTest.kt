package br.andrew.nota.agil.softexpert

import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConfiguration
import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConsultaParser
import br.andrew.nota.agil.softexpert.infrastructure.SoftexpertWebClientConfig
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftexpertApiClient
import br.andrew.nota.agil.softexpert.service.SoftExpertConsultaService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/** Somente consultas; nao carregar jobs, MongoDB nem os testes de integracao que criam workflows. */
@EnabledIfEnvironmentVariable(named = "SOFTEXPERT_READONLY_IT", matches = "true")
@SpringBootTest(classes = [SoftexpertWebClientConfig::class, SoftExpertConsultaParser::class, SoftExpertConsultaService::class])
@ActiveProfiles("hmg")
class SoftExpertConsultaReadOnlyIntegrationTest {
    @Autowired lateinit var api: SoftexpertApiClient
    @Autowired lateinit var consulta: SoftExpertConsultaService
    @Autowired lateinit var props: SoftExpertConfiguration

    @Test
    fun `deve consultar instancias de outro usuario e dados do iniciador na homologacao`() {
        assertEquals("https://rovema-test.softexpert.app", props.url.trimEnd('/'))
        val instancias = api.workflow.search("2", fgStatus = 1, fgLanguage = 2, qtLimit = 2).getOrThrow()
        assertFalse(instancias.results.isEmpty(), "Usuario de teste cdUser=2 sem instancias em andamento")
        val identificador = checkNotNull(instancias.results.first().identifier)
        val detalhes = consulta.instancia(identificador)
        assertEquals(identificador, detalhes.workflowId)
        assertFalse(detalhes.title.isNullOrBlank())
        val matricula = checkNotNull(detalhes.starterUser)
        val usuario = consulta.usuario(matricula)
        assertEquals(matricula, usuario.matricula)
        assertFalse(usuario.nome.isNullOrBlank())
    }
}
