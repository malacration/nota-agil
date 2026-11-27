package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.infrastructure.security.Perfil
import br.andrew.nota.agil.infrastructure.security.Perfis
import br.andrew.nota.agil.services.EntradaCacheFiliais
import br.andrew.nota.agil.services.FiliaisUsuarioService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** Perfil do usuario logado e limpeza do cache das proprias filiais (chamada no logout). */
@RestController
@RequestMapping("/me")
class PerfilController(private val filiais: FiliaisUsuarioService) {

    @GetMapping
    fun me(auth: Authentication): UsuarioLogado {
        val perfil = Perfis.de(auth) ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sem perfil no Nota Agil")
        return UsuarioLogado(auth.name, perfil)
    }

    @DeleteMapping("/cache-filiais")
    fun limparMeuCache(auth: Authentication): Map<String, Int> = mapOf("removidas" to filiais.invalidarUsuario(auth.name))
}

/** Administracao do cache de filiais por usuario (so admin; ver SecurityWebConf). */
@RestController
@RequestMapping("/admin/cache-filiais")
class CacheFiliaisController(private val filiais: FiliaisUsuarioService) {

    @GetMapping
    fun listar(): CacheFiliais = CacheFiliais(filiais.ttl().toString(), filiais.entradas())

    @DeleteMapping
    fun invalidarTudo(): Map<String, Int> = mapOf("removidas" to filiais.invalidarTudo())

    @DeleteMapping("/{login}")
    fun invalidarUsuario(@PathVariable login: String): Map<String, Int> = mapOf("removidas" to filiais.invalidarUsuario(login))

    @ExceptionHandler(ResponseStatusException::class)
    fun erro(e: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(e.statusCode).body(mapOf("mensagem" to (e.reason ?: "Erro")))
}

data class UsuarioLogado(val login: String, val perfil: Perfil)

data class CacheFiliais(val ttl: String, val entradas: List<EntradaCacheFiliais>)
