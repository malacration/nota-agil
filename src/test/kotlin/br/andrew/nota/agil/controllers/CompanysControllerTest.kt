package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.erp.*
import br.andrew.nota.agil.erp.sap.SapConsultaErpClient
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.*
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.support.anyValue as any
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.context.ApplicationEventPublisher
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.interfaces.Status
import br.andrew.nota.agil.qive.interfaces.controllers.Company as QiveCompany
import br.andrew.nota.agil.model.CompanyChanged

class CompanysControllerTest {
    private val repository = mock(CompanyRepository::class.java)
    private val qive = mock(QiveApiClient::class.java)
    private val publisher = mock(ApplicationEventPublisher::class.java)
    private val company = Company("12345678000190", "Empresa teste", Erp.SAP)
    private val implemented = object : ConsultaErpClient {
        override val erp = Erp.SAP
        override val consultaPagamentoImplementada = true
        override fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp =
            error("Cadastro nao pode consultar o ERP")
    }

    private fun mvc(clients: List<ConsultaErpClient> = listOf(implemented, DealerConsultaErpClient())) =
        MockMvcBuilders.standaloneSetup(CompanysController(repository, qive, CatalogoConsultaErp(clients), publisher, mock(br.andrew.nota.agil.services.FiliaisErpService::class.java)))
            .setMessageConverters(MappingJackson2HttpMessageConverter(ObjectMapper().registerModule(KotlinModule.Builder().build())))
            .build()

    @Test
    fun `catalogo deve listar apenas consultas implementadas e excluir esqueletos`() {
        mvc().perform(get("/companys/erps-disponiveis"))
            .andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].erp").value("SAP"))
        mvc(listOf(SapConsultaErpClient(), DealerConsultaErpClient())).perform(get("/companys/erps-disponiveis"))
            .andExpect(status().isOk).andExpect(content().json("[]"))
        verifyNoInteractions(repository, qive)
    }

    @Test
    fun `cadastro deve aceitar ERP implementado ou nenhum`() {
        `when`(repository.save(any())).thenAnswer { it.getArgument<Company>(0) }
        for (erp in listOf("\"SAP\"", "null")) {
            mvc().perform(post("/companys").contentType(MediaType.APPLICATION_JSON)
                .content("""{"cnpj":"12345678000190","nome":"Empresa teste","erpPagamento":$erp,"vinculadoQive":false}"""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.cnpj").value("12345678000190"))
        }
    }

    @Test
    fun `cadastro manual deve normalizar CNPJ sem consultar Qive e permitir vinculo posterior`() {
        val manual = company.copy(vinculadoQive = false)
        `when`(repository.save(any())).thenAnswer { it.getArgument<Company>(0) }
        mvc().perform(post("/companys").contentType(MediaType.APPLICATION_JSON)
            .content("""{"cnpj":"12.345.678/0001-90","nome":"Manual","vinculadoQive":false}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.cnpj").value(company.cnpj))
            .andExpect(jsonPath("$.vinculadoQive").value(false))
        verifyNoInteractions(qive)
        val api = mock(QiveCompany::class.java)
        `when`(qive.company).thenReturn(api)
        `when`(api.empresas()).thenReturn(ReceivedResponse(listOf(company.cnpj), null, null, null, Status(200, "OK")))
        `when`(repository.findById(company.cnpj)).thenReturn(Optional.of(manual))
        mvc().perform(put("/companys/${company.cnpj}/qive"))
            .andExpect(status().isOk).andExpect(jsonPath("$.vinculadoQive").value(true))
            .andExpect(jsonPath("$.erpPagamento").value("SAP"))
            .andExpect(jsonPath("$.nome").value(company.nome))
        verify(publisher).publishEvent(CompanyChanged(manual.copy(vinculadoQive = true)))
    }

    @Test
    fun `vinculo Qive deve rejeitar CNPJ indisponivel e empresa inexistente`() {
        val api = mock(QiveCompany::class.java)
        `when`(qive.company).thenReturn(api)
        `when`(api.empresas()).thenReturn(ReceivedResponse(emptyList(), null, null, null, Status(200, "OK")))
        `when`(repository.findById(company.cnpj)).thenReturn(Optional.of(company.copy(vinculadoQive = false)))
        mvc().perform(put("/companys/${company.cnpj}/qive")).andExpect(status().isBadRequest)
        `when`(repository.findById("inexistente")).thenReturn(Optional.empty())
        mvc().perform(put("/companys/inexistente/qive")).andExpect(status().isNotFound)
        verify(repository, never()).save(any())
        verifyNoInteractions(publisher)
    }

    @Test
    fun `cadastro deve rejeitar CNPJ e nome invalidos`() {
        for (body in listOf("""{"cnpj":"123","nome":"Manual","vinculadoQive":false}""",
            """{"cnpj":"12345678000190","nome":" ","vinculadoQive":false}""")) {
            mvc().perform(post("/companys").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(repository, qive, publisher)
    }

    @Test
    fun `cadastro deve rejeitar ERP nao implementado antes de salvar`() {
        mvc().perform(post("/companys").contentType(MediaType.APPLICATION_JSON)
            .content("""{"cnpj":"12345678000190","nome":"Empresa teste","erpPagamento":"DEALER"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.mensagem").value("Consulta de pagamento no ERP DEALER ainda nao implementada"))
        verifyNoInteractions(repository)
    }

    @Test
    fun `atualizar deve vincular ERP implementado e permitir remover preservando empresa`() {
        `when`(repository.findById(company.cnpj)).thenReturn(Optional.of(company))
        `when`(repository.save(any())).thenAnswer { it.getArgument<Company>(0) }
        for (erp in listOf("\"SAP\"", "null")) {
            mvc().perform(put("/companys/${company.cnpj}/erp").contentType(MediaType.APPLICATION_JSON)
                .content("""{"erpPagamento":$erp}"""))
                .andExpect(status().isOk).andExpect(jsonPath("$.nome").value(company.nome))
                .andExpect(jsonPath("$.erpPagamento").value(if (erp == "null") null else "SAP"))
        }
    }

    @Test
    fun `atualizar deve rejeitar ERP nao implementado ou desconhecido sem salvar`() {
        for (erp in listOf("DEALER", "INEXISTENTE")) {
            mvc().perform(put("/companys/${company.cnpj}/erp").contentType(MediaType.APPLICATION_JSON)
                .content("""{"erpPagamento":"$erp"}"""))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(repository)
    }

    @Test
    fun `atualizar deve retornar 404 para empresa inexistente`() {
        `when`(repository.findById("inexistente")).thenReturn(Optional.empty())
        mvc().perform(put("/companys/inexistente/erp").contentType(MediaType.APPLICATION_JSON)
            .content("""{"erpPagamento":null}"""))
            .andExpect(status().isNotFound)
        verify(repository, never()).save(any())
    }
}
