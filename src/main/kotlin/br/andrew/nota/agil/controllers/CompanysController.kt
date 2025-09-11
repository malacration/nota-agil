package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.repository.CompanyRepository
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
@RequestMapping("/companys")
class CompanysController(
    val repository : CompanyRepository,
    val qiveApi : QiveApiClient
) {

    @GetMapping()
    fun index() : List<Company> {
        return repository.findAll()
    }

    @GetMapping("cnpjs-disponiveis")
    fun cnpjsDisponiveis() : List<String> {
        return qiveApi.company.empresas().data
    }

    @PostMapping()
    fun save(@RequestBody entry : Company) : Company {
        return repository.save(entry)
    }

    @DeleteMapping("{cnpj}")
    fun save(@PathVariable cnpj : String) {
        return repository.deleteById(cnpj)
    }
}