package br.andrew.nota.agil.security

import br.andrew.nota.agil.controllers.AuthConfigController
import br.andrew.nota.agil.infrastructure.security.CorsConfig
import br.andrew.nota.agil.infrastructure.security.SecurityWebConf
import br.andrew.nota.agil.infrastructure.security.keycloak.*
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.time.Instant
import java.util.Date

class KeycloakSecurityTest {
    private val properties = KeycloakProperties()
    private val keys = mock(KeycloakJwksProvider::class.java).also {
        `when`(it.chave(anyString())).thenReturn(pair.public)
    }
    private val context = GenericWebApplicationContext().apply {
        servletContext = MockServletContext()
        beanFactory.registerSingleton("keycloakProperties", properties)
        beanFactory.registerSingleton("keycloakJwtService", KeycloakJwtService(properties, keys))
        AnnotatedBeanDefinitionReader(this).register(SecurityWebConf::class.java, CorsConfig::class.java,
            AuthConfigController::class.java, MvcConfig::class.java, ProtectedController::class.java)
        refresh()
    }
    private val mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters<DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter::class.java)).build()

    @AfterEach
    fun close() = context.close()

    @Test
    fun `configuracao publica deve refletir o SSO de producao sem expor segredos`() {
        mvc.perform(get("/auth/config")).andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("keycloak"))
            .andExpect(jsonPath("$.keycloak.url").value("https://sso.gruporovema.com.br"))
            .andExpect(jsonPath("$.keycloak.realm").value("rovema"))
            .andExpect(jsonPath("$.keycloak.clientId").value("nota-agil"))
            .andExpect(jsonPath("$.keycloak.clientSecret").doesNotExist())
        verifyNoInteractions(keys)
    }

    @Test
    fun `rota protegida sem token deve retornar 401 com desafio Bearer`() {
        mvc.perform(get("/test/protegido")).andExpect(status().isUnauthorized)
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
    }

    @Test
    fun `access token RS256 valido deve autenticar e disponibilizar roles`() {
        mvc.perform(get("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.usuario").value("usuario.teste"))
            .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.containsInAnyOrder("ROLE_admin", "ROLE_consulta")))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        mvc.perform(get("/test/protegido")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `post com token valido deve funcionar sem sessao nem CSRF`() {
        mvc.perform(post("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer ${token()}"))
            .andExpect(status().isOk)
    }

    @Test
    fun `deve rejeitar assinatura issuer client expiracao e tipo de token invalidos`() {
        val invalidos = listOf(
            token(issuer = "https://outro.example/realms/rovema"),
            token(client = "outro-client"), token(expira = Instant.now().minusSeconds(120)),
            token(tipo = "ID"), token(semExpiracao = true), token(outraChave = true),
            "nao-e-jwt", Jwts.builder().subject("teste").signWith(Jwts.SIG.HS256.key().build()).compact(),
        )
        invalidos.forEach { jwt ->
            mvc.perform(get("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer $jwt"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `motivo da recusa identifica a validacao sem expor o token`() {
        val service = context.getBean(KeycloakJwtService::class.java)
        val casos = mapOf(
            token(issuer = "https://outro.example/realms/rovema") to "claim 'iss' com valor diferente do esperado",
            token(tipo = "ID") to "claim 'typ' com valor diferente do esperado",
            token(client = "outro-client") to "Client do token nao permitido",
            token(semSubject = true) to "Access token incompleto",
            token(expira = Instant.now().minusSeconds(120)) to "token expirado",
            token(outraChave = true) to "assinatura invalida",
        )
        casos.forEach { (jwt, esperado) ->
            val erro = assertThrows<Exception> { service.validar(jwt) }
            val motivo = KeycloakAuthenticationFilter.motivo(erro)
            assertEquals(esperado, motivo)
            jwt.split('.').forEach { parte -> assertFalse(motivo.contains(parte)) }
        }
    }

    @Test
    fun `nao deve existir usuario local com senha gerada pelo Spring`() {
        val usuarios = context.getBean(UserDetailsService::class.java)
        assertThrows<UsernameNotFoundException> { usuarios.loadUserByUsername("user") }
    }

    @Test
    fun `client de integracao adicional precisa estar explicitamente permitido`() {
        properties.allowedClientIds = setOf("nota-agil-integracao")
        mvc.perform(post("/integracoes/teste").header(HttpHeaders.AUTHORIZATION,
            "Bearer ${token(client = "nota-agil-integracao", realmRoles = emptyList())}"))
            .andExpect(status().isOk)
    }

    @Test
    fun `integracao so alcanca rotas de integracao, mesmo com role admin no realm`() {
        properties.allowedClientIds = setOf("nota-agil-integracao")
        val integracao = token(client = "nota-agil-integracao", realmRoles = emptyList())
        mvc.perform(get("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer $integracao")).andExpect(status().isForbidden)
        mvc.perform(get("/erp/notas-entrada/erps").header(HttpHeaders.AUTHORIZATION, "Bearer $integracao")).andExpect(status().isForbidden)
    }

    @Test
    fun `integracao com roles admin e operador no realm e no client nao herda acesso humano`() {
        properties.allowedClientIds = setOf("nota-agil-integracao")
        val integracao = token(client = "nota-agil-integracao", realmRoles = listOf("admin", "operador"),
            clientRoles = listOf("admin", "operador"))
        for (rota in listOf("/test/protegido", "/erp/notas-entrada/erps", "/me"))
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, "Bearer $integracao")).andExpect(status().isForbidden)
        mvc.perform(post("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer $integracao")).andExpect(status().isForbidden)
        mvc.perform(post("/integracoes/teste").header(HttpHeaders.AUTHORIZATION, "Bearer $integracao")).andExpect(status().isOk)
    }

    @Test
    fun `perfil de integracao prevalece sobre admin e operador`() {
        val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken("svc", null,
            listOf("ROLE_admin", "ROLE_operador", "ROLE_INTEGRACAO").map { org.springframework.security.core.authority.SimpleGrantedAuthority(it) })
        assertEquals(br.andrew.nota.agil.infrastructure.security.Perfil.INTEGRACAO,
            br.andrew.nota.agil.infrastructure.security.Perfis.de(auth))
    }

    @Test
    fun `operador so alcanca notas ERP e o proprio perfil`() {
        val operador = token(realmRoles = listOf("operador"))
        mvc.perform(get("/erp/notas-entrada/erps").header(HttpHeaders.AUTHORIZATION, "Bearer $operador")).andExpect(status().isOk)
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer $operador")).andExpect(status().isOk)
        mvc.perform(get("/test/protegido").header(HttpHeaders.AUTHORIZATION, "Bearer $operador"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("perfil")))
        mvc.perform(post("/integracoes/teste").header(HttpHeaders.AUTHORIZATION, "Bearer $operador")).andExpect(status().isForbidden)
    }

    @Test
    fun `usuario sem perfil recebe 403 em tudo e admin alcanca tudo`() {
        val semPerfil = token(realmRoles = listOf("outra"))
        for (rota in listOf("/test/protegido", "/erp/notas-entrada/erps", "/me"))
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, "Bearer $semPerfil")).andExpect(status().isForbidden)
        val admin = token()
        for (rota in listOf("/test/protegido", "/erp/notas-entrada/erps", "/me"))
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, "Bearer $admin")).andExpect(status().isOk)
        mvc.perform(post("/integracoes/teste").header(HttpHeaders.AUTHORIZATION, "Bearer $admin")).andExpect(status().isOk)
    }

    @Test
    fun `preflight CORS nao deve exigir token`() {
        mvc.perform(options("/test/protegido").header(HttpHeaders.ORIGIN, "http://localhost:4200")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
    }

    private fun token(
        issuer: String = properties.issuer(), client: String = properties.clientId,
        expira: Instant = Instant.now().plusSeconds(300), tipo: String = "Bearer",
        semExpiracao: Boolean = false, outraChave: Boolean = false, semSubject: Boolean = false,
        realmRoles: List<String> = listOf("admin"), clientRoles: List<String> = listOf("consulta"),
    ): String {
        val builder = Jwts.builder().header().keyId("chave-1").and().issuer(issuer)
        if (!semSubject) builder.subject("usuario-1")
        builder
            .claim("preferred_username", "usuario.teste").claim("typ", tipo).claim("azp", client)
            .claim("realm_access", mapOf("roles" to realmRoles))
            .claim("resource_access", mapOf(properties.clientId to mapOf("roles" to clientRoles)))
        if (!semExpiracao) builder.expiration(Date.from(expira))
        return builder.signWith(if (outraChave) otherPair.private else pair.private, Jwts.SIG.RS256).compact()
    }

    @Configuration
    @EnableWebMvc
    class MvcConfig

    @RestController
    class ProtectedController {
        @GetMapping("/test/protegido")
        fun get(auth: Authentication) = mapOf("usuario" to auth.name, "roles" to auth.authorities.map { it.authority })
        @PostMapping("/test/protegido")
        fun post() = mapOf("ok" to true)
        @PostMapping("/integracoes/teste")
        fun integracao() = mapOf("ok" to true)
        @GetMapping("/erp/notas-entrada/erps")
        fun erps() = emptyList<String>()
        @GetMapping("/me")
        fun me(auth: Authentication) = mapOf("usuario" to auth.name)
    }

    companion object {
        private val pair = Jwts.SIG.RS256.keyPair().build()
        private val otherPair = Jwts.SIG.RS256.keyPair().build()
    }
}
