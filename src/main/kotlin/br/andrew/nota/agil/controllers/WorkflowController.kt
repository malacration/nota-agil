package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertResults
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftexpertApiClient
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.WorkflowInstance
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import br.andrew.nota.agil.softexpert.service.SoftExpertConsultaService
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.WorkflowDetails
import br.andrew.wsdl.workflow.NewWorkflowEditDataResponseType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.Date

@RestController()
@RequestMapping("/workflow")
class WorkflowController(
    val service : WorkFlowService,
    val api : SoftexpertApiClient,
    val consulta: SoftExpertConsultaService,
) {

    @GetMapping()
    fun index(
        @RequestParam(required = false) cdUser : String? = null,
        @RequestParam(required = false) fgStatus : Int? = null,
        @RequestHeader(name = "cdUser", required = false) cdUserHeader : String? = null,
        @RequestParam(required = false) qtLimit: Int? = null,
        @RequestParam(required = false) fgLanguage: Int? = null,
    ) : SoftExpertResults<WorkflowInstance> {
        val usuario = (cdUser ?: cdUserHeader)?.trim()
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "cdUser nao informado no param nem no header")
        if (usuario.toLongOrNull()?.let { it > 0 } != true) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "cdUser deve ser um codigo interno numerico positivo")
        }
        if (qtLimit != null && qtLimit < 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "qtLimit deve ser positivo")
        if (fgLanguage != null && fgLanguage < 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "fgLanguage deve ser positivo")
        return api.workflow.search(usuario, fgStatus = fgStatus, qtLimit = qtLimit, fgLanguage = fgLanguage).getOrThrow()
    }

    @GetMapping("/{workflowId}")
    fun detalhes(@PathVariable workflowId: String): WorkflowDetails = consulta.instancia(workflowId)

    @GetMapping("/new/{titulo}")
    fun new(@PathVariable titulo : String) : NewWorkflowEditDataResponseType {
        val idProcesso = "FXP-NA"
        val idTabela = "fxpna"


        val dados = mapOf<String,Map<String,String>>(
            "fxpna" to mapOf(
                "empresa" to "jb-empresa",
            )
        )
        return service.instanciaFluxo("FXP-NA","usando soap",dados)
//        return api.workflow.newCreate(request)
    }
}

class Attribute(val entityAttributeID : String, val entityAttributeValue : String)
