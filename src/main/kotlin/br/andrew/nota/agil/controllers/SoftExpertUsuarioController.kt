package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertUser
import br.andrew.nota.agil.softexpert.service.SoftExpertConsultaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/softexpert/usuarios")
class SoftExpertUsuarioController(private val service: SoftExpertConsultaService) {
    @GetMapping("/{matricula}")
    fun buscar(@PathVariable matricula: String): SoftExpertUser = service.usuario(matricula)
}
