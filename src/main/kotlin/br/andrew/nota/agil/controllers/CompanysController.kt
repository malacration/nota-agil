package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.CompanyChanged
import br.andrew.nota.agil.model.erp.normalizaCnpj
import org.springframework.context.ApplicationEventPublisher
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.erp.CatalogoConsultaErp
import br.andrew.nota.agil.erp.ErpDisponivel
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.services.FiliaisErpService
import br.andrew.nota.agil.services.ImportarFiliaisErpRequest
import br.andrew.nota.agil.services.ListagemFiliaisErp
import br.andrew.nota.agil.services.ResultadoFilialErp
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import java.util.Date

@RestController()
@RequestMapping("/companys")
class CompanysController(
    val repository : CompanyRepository,
    val qiveApi : QiveApiClient,
    private val erps: CatalogoConsultaErp,
    private val events: ApplicationEventPublisher,
    private val filiaisErp: FiliaisErpService,
) {

    /** Filiais do ERP (SAP/Dealer) com a situacao no cadastro e na Qive. */
    @GetMapping("filiais-erp")
    fun filiaisErp(@RequestParam erp: Erp): ListagemFiliaisErp = filiaisErp.listar(erp)

    /** Cadastra as filiais escolhidas; vinculo com a Qive so quando o CNPJ existe la. */
    @PostMapping("filiais-erp")
    fun importarFiliaisErp(@RequestBody request: ImportarFiliaisErpRequest): List<ResultadoFilialErp> =
        filiaisErp.importar(request.erp, request.cnpjs)

    @GetMapping()
    fun index() : List<Company> {
        return repository.findAll().filter { it.cnpj != "all" }
    }

    @GetMapping("cnpjs-disponiveis")
    fun cnpjsDisponiveis() : List<String> {
        return qiveApi.company.empresas().data.map(::normalizaCnpj).filter { it.length == 14 }.distinct()
    }

    @PostMapping()
    fun save(@RequestBody entry : Company) : Company {
        erps.validar(entry.erpPagamento)
        val formato = Regex("(?:[0-9]{14}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})")
        if (!formato.matches(entry.cnpj.trim()) || entry.nome.isBlank() || entry.nome.length > 200) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um CNPJ com 14 digitos e um nome valido")
        }
        if ((entry.codigoDealer ?: 1) <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo do Dealer deve ser positivo")
        val normalized = entry.copy(cnpj = normalizaCnpj(entry.cnpj), nome = entry.nome.trim())
        if (normalized.vinculadoQive) validarVinculoQive(normalized.cnpj)
        return salvarEmpresa(normalized)
    }

    @GetMapping("erps-disponiveis")
    fun erpsDisponiveis(): List<ErpDisponivel> = erps.disponiveis()

    @PutMapping("{cnpj}/erp")
    fun atualizarErp(@PathVariable cnpj: String, @RequestBody request: AtualizarErpCompanyRequest): Company {
        erps.validar(request.erpPagamento)
        val empresa = repository.findById(cnpj).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao encontrada")
        }
        return salvarEmpresa(empresa.copy(erpPagamento = request.erpPagamento))
    }

    /** Codigo da empresa no Dealer (Titulo_EmpresaCod), usado na importacao de notas; null remove. */
    @PutMapping("{cnpj}/dealer")
    fun atualizarCodigoDealer(@PathVariable cnpj: String, @RequestBody request: AtualizarCodigoDealerRequest): Company {
        val codigo = request.codigoDealer
        if (codigo != null && codigo <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo do Dealer deve ser positivo")
        val empresa = repository.findById(cnpj).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao encontrada")
        }
        if (codigo != null && repository.findAll().any { it.cnpj != empresa.cnpj && it.codigoDealer == codigo })
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo do Dealer ja usado por outra empresa")
        return salvarEmpresa(empresa.copy(codigoDealer = codigo))
    }

    data class AtualizarCodigoDealerRequest(val codigoDealer: Int? = null)

    @PutMapping("{cnpj}/qive")
    fun vincularQive(@PathVariable cnpj: String): Company {
        val empresa = repository.findById(cnpj).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa nao encontrada")
        }
        if (empresa.vinculadoQive) return empresa
        validarVinculoQive(cnpj)
        return salvarEmpresa(empresa.copy(vinculadoQive = true))
    }

    private fun validarVinculoQive(cnpj: String) {
        if (cnpj !in cnpjsDisponiveis()) throw ResponseStatusException(HttpStatus.BAD_REQUEST,
            "CNPJ ainda nao disponivel na Qive. Cadastre sem vinculo e vincule posteriormente")
    }

    private fun salvarEmpresa(empresa: Company): Company = repository.save(empresa).also {
        events.publishEvent(CompanyChanged(it))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun erroStatus(error: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(error.statusCode).body(mapOf("mensagem" to (error.reason ?: "Erro no cadastro de empresa")))

    @DeleteMapping("{cnpj}")
    fun save(@PathVariable cnpj : String) {
        if (cnpj == "all") throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa reservada ao sistema")
        val empresa = repository.findById(cnpj).orElse(null)
        repository.deleteById(cnpj)
        empresa?.let { events.publishEvent(CompanyChanged(it.copy(vinculadoQive = false))) }
    }
}

data class AtualizarErpCompanyRequest(val erpPagamento: Erp?)
