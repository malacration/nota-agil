package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.tasks.*
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.services.TaskService
import br.andrew.nota.agil.support.anyValue as any
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TasksControllerTest {
    private val mongo = mock(MongoTemplate::class.java)
    private val worker = mock(TaskService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(TasksController(mock(TaskRepository::class.java), worker,
        mock(QiveApiClient::class.java), mongo))
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .setMessageConverters(MappingJackson2HttpMessageConverter(ObjectMapper().findAndRegisterModules())).build()

    @Test
    fun `listagem existente deve retornar tipos antigos e ERP na mesma pagina`() {
        val data = listOf(Task("erp", "Pagamento WF1", TaskStatus.READY, TaskTypes.ConsultaErp),
            Task("pdf", "Documento", TaskStatus.READY, TaskTypes.UploadPdf))
        `when`(mongo.count(any(), eq(Task::class.java))).thenReturn(2)
        `when`(mongo.find(any(), eq(Task::class.java))).thenReturn(data)
        mvc.perform(get("/tasks"))
            .andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].taskType").value("ConsultaErp"))
            .andExpect(jsonPath("$.content[1].taskType").value("UploadPdf"))
        verifyNoInteractions(worker)
    }

    @Test
    fun `listagem deve aceitar filtro ERP e rejeitar tipo desconhecido e tamanho excessivo`() {
        `when`(mongo.count(any(), eq(Task::class.java))).thenReturn(0)
        `when`(mongo.find(any(), eq(Task::class.java))).thenReturn(emptyList<Task>())
        mvc.perform(get("/tasks").param("taskType", "ConsultaErp")).andExpect(status().isOk)
        mvc.perform(get("/tasks").param("taskType", "INVALIDO")).andExpect(status().isBadRequest)
        mvc.perform(get("/tasks").param("size", "101")).andExpect(status().isBadRequest)
    }
}
