package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.services.EmpresaNotasErp
import br.andrew.nota.agil.services.ErpNotasDisponivel
import br.andrew.nota.agil.services.ImportacaoNotasErp
import br.andrew.nota.agil.services.ImportarNotasErpRequest
import br.andrew.nota.agil.services.ListagemNotasErp
import br.andrew.nota.agil.services.NotasErpService
import br.andrew.nota.agil.services.FiliaisUsuarioService
import org.springframework.security.core.Authentication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Notas fiscais de entrada (contas a pagar) dos ERPs, fonte alternativa ao Qive, sob demanda: o
 * usuario escolhe o ERP (obrigatorio), lista as notas de uma empresa e escolhe quais viram task.
 * Operador so ve e usa as filiais que o ERP libera para ele ([FiliaisUsuarioService]); admin, todas.
 */
@RestController
@RequestMapping("/erp/notas-entrada")
class NotasErpController(private val service: NotasErpService, private val filiais: FiliaisUsuarioService) {

    /** ERPs e disponibilidade (flag `NOTAS_ERP_<ERP>_ENABLED` + variaveis de conexao). */
    @GetMapping("/erps")
    fun erps(): List<ErpNotasDisponivel> = service.erps()

    /** Empresas cadastradas e se podem listar notas no ERP (ex.: Dealer exige o codigo da empresa). */
    @GetMapping("/empresas")
    fun empresas(@RequestParam erp: Erp, auth: Authentication?): List<EmpresaNotasErp> {
        val permitidas = filiais.permitidas(auth, erp)
        return service.empresas(erp) { empresa -> permitidas?.permite(erp, empresa) ?: true }
    }

    @GetMapping
    fun listar(
        @RequestParam erp: Erp,
        @RequestParam cnpj: String,
        @RequestParam dataInicial: String,
        @RequestParam dataFinal: String,
        @RequestParam(required = false) fornecedor: String?,
        @RequestParam(required = false) numero: String?,
        auth: Authentication?,
    ): ListagemNotasErp {
        // A permissao e conferida na empresa cadastrada (CNPJ e, no Dealer, o codigo usado na consulta).
        return service.listar(erp, cnpj, dataInicial, dataFinal, fornecedor, numero) { filiais.exigirPermitido(auth, erp, it) }
    }

    /** Cria a task CreateTask (READY) de cada nota escolhida; resultado por nota. */
    @PostMapping("/tasks")
    fun importar(@RequestBody request: ImportarNotasErpRequest, auth: Authentication?): ImportacaoNotasErp {
        val erp = request.erp ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "erp e obrigatorio")
        val cnpj = request.cnpj ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "cnpj e obrigatorio")
        return service.importar(erp, cnpj, request.ids.orEmpty()) { filiais.exigirPermitido(auth, erp, it) }
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun erro(e: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(e.statusCode).body(mapOf("mensagem" to (e.reason ?: "Erro na consulta de notas do ERP")))
}
