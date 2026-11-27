package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.controllers.handler.SoftExpertConsultaExceptionHandler
import br.andrew.nota.agil.softexpert.infrastructure.ApiException
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.*
import br.andrew.nota.agil.softexpert.service.SoftExpertConsultaService
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class SoftExpertConsultaControllerTest {
    private val workflow = mock(Workflow::class.java)
    private val consulta = mock(SoftExpertConsultaService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(
        WorkflowController(mock(WorkFlowService::class.java), SoftexpertApiClient(workflow), consulta),
        SoftExpertUsuarioController(consulta),
    ).setControllerAdvice(SoftExpertConsultaExceptionHandler())
        .setMessageConverters(MappingJackson2HttpMessageConverter(ObjectMapper().registerModule(KotlinModule.Builder().build())))
        .build()

    @Test
    fun `deve listar instancias de outro usuario com todos os filtros`() {
        `when`(workflow.search("2", fgStatus = 1, qtLimit = 2, fgLanguage = 2))
            .thenReturn(SoftExpertResponse(true, SoftExpertResults(1, listOf(WorkflowInstance(identifier = "WF001")))))
        mvc.perform(get("/workflow").param("cdUser", "2").param("fgStatus", "1")
            .param("qtLimit", "2").param("fgLanguage", "2"))
            .andExpect(status().isOk).andExpect(jsonPath("$.results[0].identifier").value("WF001"))
        verify(workflow).search("2", fgStatus = 1, qtLimit = 2, fgLanguage = 2)
    }

    @Test
    fun `deve preservar cdUser via header e aceitar retorno vazio`() {
        `when`(workflow.search("1")).thenReturn(SoftExpertResponse(true, SoftExpertResults()))
        mvc.perform(get("/workflow").header("cdUser", "1"))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `deve rejeitar falta de usuario e filtros invalidos antes de consultar SE`() {
        listOf("/workflow", "/workflow?cdUser=", "/workflow?cdUser=abc", "/workflow?cdUser=0",
            "/workflow?cdUser=2&qtLimit=0", "/workflow?cdUser=2&fgLanguage=-1",
            "/workflow?cdUser=2&fgStatus=abc").forEach { url ->
            mvc.perform(get(url)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(workflow)
    }

    @Test
    fun `deve retornar detalhes com matricula do iniciador`() {
        `when`(consulta.instancia("WF001")).thenReturn(WorkflowDetails("WF001", "Teste", "FXP-NA", "1",
            "Em analise", "001", null, "2026-11-11 10:00:00"))
        mvc.perform(get("/workflow/WF001")).andExpect(status().isOk)
            .andExpect(jsonPath("$.starterUser").value("001"))
            .andExpect(jsonPath("$.processId").value("FXP-NA"))
    }

    @Test
    fun `deve consultar usuario por matricula preservando zeros`() {
        `when`(consulta.usuario("001")).thenReturn(SoftExpertUser("001", "teste", "Teste", null, null,
            emptyList(), emptyList(), emptyList()))
        mvc.perform(get("/softexpert/usuarios/001")).andExpect(status().isOk)
            .andExpect(jsonPath("$.matricula").value("001"))
            .andExpect(jsonPath("$.nome").value("Teste"))
        verify(consulta).usuario("001")
    }

    @Test
    fun `deve preservar erros de consulta de usuario e instancia`() {
        `when`(consulta.usuario("naoexiste")).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"))
        mvc.perform(get("/softexpert/usuarios/naoexiste")).andExpect(status().isNotFound)
        `when`(consulta.usuario("bloqueado")).thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissao"))
        mvc.perform(get("/softexpert/usuarios/bloqueado")).andExpect(status().isForbidden)
        `when`(consulta.instancia("invalido")).thenThrow(ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta invalida"))
        mvc.perform(get("/workflow/invalido")).andExpect(status().isBadGateway)
    }

    @Test
    fun `deve retornar 502 quando API REST do SE falhar`() {
        `when`(workflow.search("2")).thenThrow(ApiException(403, "Sem permissao"))
        mvc.perform(get("/workflow").param("cdUser", "2")).andExpect(status().isBadGateway)
    }
}
