package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaProdutos
import br.andrew.nota.agil.qive.model.NotaServico
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Date

@RestController()
class IndexController(val qiveApi : QiveApiClient) {

    @GetMapping()
    fun index(@RequestParam(name = "cursor", defaultValue = "0") cursor : Int ) : ReceivedResponse<Any> {
        return qiveApi.events.listReceived(cursor = cursor)
    }

    @GetMapping("nfe/{cnpj}")
    fun nfeByCnpj(
        @PathVariable cnpj : String,
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from : Date?,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to : Date?,
        @RequestParam(name = "cursor") cursor : Int? = null) : ReceivedResponse<NotaProdutos> {
        return qiveApi.nfe.listReceived(listOf(cnpj),cursor,from,to)
    }

    @GetMapping("nfse/{cnpj}")
    fun nfseByCnpj(
        @PathVariable cnpj : String,
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from : Date?,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to : Date?,
        @RequestParam(name = "cursor") cursor : Int? = null) : ReceivedResponse<NotaServico> {
        return qiveApi.nfse.listReceived(listOf(cnpj),cursor,from,to)
    }

    @GetMapping("event/{cnpj}")
    fun events(@PathVariable cnpj : String,
               @RequestParam(name = "cursor", defaultValue = "0") cursor : Int ) : ReceivedResponse<Any> {
        return qiveApi.events.listReceived(cursor = cursor)
    }

    @GetMapping("/empresa")
    fun empresas() : ReceivedResponse<String> {
        return qiveApi.company.empresas()
    }
}