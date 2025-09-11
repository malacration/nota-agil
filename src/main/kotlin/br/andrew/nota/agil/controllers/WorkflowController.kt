package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftexpertApiClient
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import br.andrew.wsdl.workflow.NewWorkflowEditDataResponseType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Date

@RestController()
@RequestMapping("/workflow")
class WorkflowController(
    val service : WorkFlowService,
    val api : SoftexpertApiClient,
) {

    @GetMapping()
    fun index() : String {
        return api.workflow.search(7)
    }

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