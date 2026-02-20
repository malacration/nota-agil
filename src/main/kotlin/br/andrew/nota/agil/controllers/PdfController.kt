package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pdf")
class PdfController(
    private val qiveApi: QiveApiClient,
    private val workFlowService: WorkFlowService
) {

    @GetMapping("/nfe")
    fun danfe(
        @RequestParam("id") id: String,
    ): DocumentPdfResponse {
        return qiveApi.nfe.danfe(id)
    }

    @GetMapping("/nfse")
    fun danfse(
        @RequestParam("id") id: String,
    ): DocumentPdfResponse {
        return qiveApi.nfse.danfse(id)
    }

    @GetMapping("/cte")
    fun dacte(
        @RequestParam("id") id: String,
    ): DocumentPdfResponse {
        return qiveApi.cte.dacte(id)
    }
}
