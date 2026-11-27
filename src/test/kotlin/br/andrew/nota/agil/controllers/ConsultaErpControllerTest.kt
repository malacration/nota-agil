package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.erp.*
import br.andrew.nota.agil.services.ConsultaErpService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import br.andrew.nota.agil.support.anyValue as any
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ConsultaErpControllerTest {
    private val service = mock(ConsultaErpService::class.java)
    private val json = ObjectMapper().findAndRegisterModules().registerModule(KotlinModule.Builder().build())
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(ConsultaErpController(service))
        .setMessageConverters(MappingJackson2HttpMessageConverter(json)).build()
    private val body = """
        {"workflowId":"FXP-NA001","solicitacaoId":"baixa-1",
         "cpfcnpjtomador":"12345678000190","cnpjprestador":"98765432000100",
         "numdocfiscal":"000123","valorparcela":125.50,"seriedocfiscal":"001","tipodoc":"Nfse"}
    """.trimIndent()
    private val task = ConsultaErpTask("task-1", json.readValue(body, RegistrarConsultaErpRequest::class.java),
        "integracao-erp", 1, 2, "fxpna")

    @Test
    fun `post deve aceitar campos da nota e retornar protocolo com location`() {
        `when`(service.registrar(any())).thenReturn(task)
        mvc.perform(post("/integracoes/erp/consultas").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted)
            .andExpect(header().string("Location", "/integracoes/erp/consultas/task-1"))
            .andExpect(jsonPath("$.id").value("task-1"))
            .andExpect(jsonPath("$.status").value("PENDENTE"))
        val captor = ArgumentCaptor.forClass(RegistrarConsultaErpRequest::class.java)
        verify(service).registrar(captor.capture() ?: task.requisicao)
        assertEquals("000123", captor.value.numDocFiscal)
        assertEquals(BigDecimal("125.50"), captor.value.valorParcela)
    }

    @Test
    fun `post deve aceitar aliases ilustrativos e tipo de documento opcional`() {
        `when`(service.registrar(any())).thenReturn(task)
        val aliases = """{"workflowId":"XPTO","solicitacaoId":"1","cnpjTomador":"12.345.678/0001-90",
            "cnpjFornecedor":"98765432000100","numeroNota":"1","valorParcela":10,"serieNota":"A"}"""
        mvc.perform(post("/integracoes/erp/consultas").contentType(MediaType.APPLICATION_JSON).content(aliases))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `post deve rejeitar cnpj invalido valor nao positivo e nota vazia`() {
        listOf(
            body.replace("12345678000190", "123"),
            body.replace("125.50", "0"),
            body.replace("000123", " "),
            body.replace("baixa-1", ""),
        ).forEach { invalido ->
            mvc.perform(post("/integracoes/erp/consultas").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `post deve retornar 400 para json incompleto ou malformado`() {
        listOf("{}", "{").forEach {
            mvc.perform(post("/integracoes/erp/consultas").contentType(MediaType.APPLICATION_JSON).content(it))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `post deve preservar 409 e 503 do registro`() {
        listOf(HttpStatus.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE).forEach { codigo ->
            doThrow(ResponseStatusException(codigo, "Configuracao ou conflito")).`when`(service).registrar(any())
            mvc.perform(post("/integracoes/erp/consultas").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().`is`(codigo.value()))
                .andExpect(jsonPath("$.mensagem").value("Configuracao ou conflito"))
        }
    }

    @Test
    fun `listar deve retornar pagina com nota status e dados de acompanhamento sem executar`() {
        `when`(service.listar(1, 2, StatusConsultaErp.PENDENTE, "FXP-NA001"))
            .thenReturn(PageImpl(listOf(task), PageRequest.of(1, 2), 3))
        mvc.perform(get("/integracoes/erp/consultas").param("page", "1").param("size", "2")
            .param("status", "PENDENTE").param("workflowId", "FXP-NA001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.number").value(1))
            .andExpect(jsonPath("$.content[0].requisicao.numdocfiscal").value("000123"))
            .andExpect(jsonPath("$.content[0].status").value("PENDENTE"))
            .andExpect(jsonPath("$.content[0].reserva").doesNotExist())
            .andExpect(jsonPath("$.content[0].atividadeId").doesNotExist())
        verify(service).listar(1, 2, StatusConsultaErp.PENDENTE, "FXP-NA001")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `listar deve retornar 400 para status ou pagina nao reconhecidos`() {
        mvc.perform(get("/integracoes/erp/consultas").param("status", "INVALIDO")).andExpect(status().isBadRequest)
        mvc.perform(get("/integracoes/erp/consultas").param("page", "abc")).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `get deve retornar acompanhamento sem executar a tarefa`() {
        `when`(service.buscar("task-1")).thenReturn(task)
        mvc.perform(get("/integracoes/erp/consultas/task-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.workflowId").value("FXP-NA001"))
        verify(service).buscar("task-1")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `get deve retornar 404 para protocolo inexistente`() {
        `when`(service.buscar("inexistente")).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Nao encontrada"))
        mvc.perform(get("/integracoes/erp/consultas/inexistente")).andExpect(status().isNotFound)
    }
}
