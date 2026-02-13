package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaProdutos
import br.andrew.nota.agil.qive.model.NotaServico
import br.andrew.nota.agil.repository.TaskRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@RestController()
class IndexController(
    val qiveApi : QiveApiClient,
    val taskRepository: TaskRepository
) {

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

    @GetMapping("nfe-nao-cadastradas/{cnpj}")
    fun nfeNaoCadastradas(
        @PathVariable cnpj : String,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date : LocalDate,
        @RequestParam(name = "cursor") cursor : Int? = null
    ) : ReceivedResponse<NotaProdutos> {
        val zone = ZoneId.systemDefault()
        val from = Date.from(date.atStartOfDay(zone).toInstant())
        val to = Date.from(date.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant())

        val resposta = qiveApi.nfe.listReceived(listOf(cnpj), cursor, from, to)
        if (resposta.data.isEmpty()) {
            return ReceivedResponse(
                data = emptyList(),
                page = resposta.page,
                count = 0,
                signatura = resposta.signatura,
                status = resposta.status
            )
        }

        val numeros = resposta.data.mapNotNull { it.getNumero() }.toSet()
        val emitentes = resposta.data.mapNotNull { it.getCpfCnpjEmitente() }.toSet()

        val tarefasExistentes = if (numeros.isEmpty() || emitentes.isEmpty()) {
            emptyList()
        } else {
            taskRepository.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(
                emitentes,
                numeros
            )
        }

        val paresExistentes = tarefasExistentes
            .mapNotNull { it.duplicata }
            .map { "${it.cpfCnpjPrestador}|${it.numero}" }
            .toSet()

        val naoCadastradas = resposta.data.filter { nota ->
            val emitente = nota.getCpfCnpjEmitente()
            val numero = nota.getNumero()
            if (emitente == null || numero == null) {
                true
            } else {
                "${emitente}|${numero}" !in paresExistentes
            }
        }

        return ReceivedResponse(
            data = naoCadastradas,
            page = resposta.page,
            count = naoCadastradas.size,
            signatura = resposta.signatura,
            status = resposta.status
        )
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
