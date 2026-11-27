package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthConfigController(private val keycloak: KeycloakProperties) {
    @GetMapping("/config")
    fun config() = AuthConfigResponse("keycloak", KeycloakClientConfig(keycloak.url, keycloak.realm, keycloak.clientId))
}

data class AuthConfigResponse(val mode: String, val keycloak: KeycloakClientConfig)
data class KeycloakClientConfig(val url: String, val realm: String, val clientId: String)
