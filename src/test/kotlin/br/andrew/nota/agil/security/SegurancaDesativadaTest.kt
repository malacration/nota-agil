package br.andrew.nota.agil.security

import br.andrew.nota.agil.controllers.AuthConfigController
import br.andrew.nota.agil.infrastructure.security.CorsConfig
import br.andrew.nota.agil.infrastructure.security.SecurityWebConf
import br.andrew.nota.agil.infrastructure.security.SegurancaDesativada
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakJwksProvider
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakJwtService
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/** Chave `nota-agil.seguranca.desativada` / `NOTA_AGIL_SECURITY_DISABLED`: teste local/homolog sem Keycloak. */
@ExtendWith(OutputCaptureExtension::class)
class SegurancaDesativadaTest {
    private val abertos = mutableListOf<GenericWebApplicationContext>()

    @AfterEach
    fun fechar() = abertos.forEach { it.close() }

    /**
     * [ambiente] simula variaveis de ambiente reais (SystemEnvironmentPropertySource, com o relaxed
     * binding do Spring: `SEGURANCA_DESATIVADA` responde por `seguranca.desativada`).
     */
    private fun contexto(
        propriedades: Map<String, Any>,
        vararg perfis: String,
        ambiente: Map<String, Any> = emptyMap(),
        perfisPadrao: List<String> = emptyList(),
    ): GenericWebApplicationContext {
        val properties = KeycloakProperties()
        val context = GenericWebApplicationContext().apply {
            servletContext = MockServletContext()
            environment.propertySources.addFirst(SystemEnvironmentPropertySource("ambiente-teste", ambiente))
            environment.propertySources.addFirst(MapPropertySource("teste", propriedades))
            environment.setActiveProfiles(*perfis)
            if (perfisPadrao.isNotEmpty()) environment.setDefaultProfiles(*perfisPadrao.toTypedArray())
            beanFactory.registerSingleton("keycloakProperties", properties)
            beanFactory.registerSingleton("keycloakJwtService",
                KeycloakJwtService(properties, mock(KeycloakJwksProvider::class.java)))
            AnnotatedBeanDefinitionReader(this).register(SegurancaDesativada::class.java, SecurityWebConf::class.java,
                CorsConfig::class.java, AuthConfigController::class.java, MvcConfig::class.java, Rotas::class.java)
        }
        abertos += context
        context.refresh()
        return context
    }

    private fun mvc(propriedades: Map<String, Any>, vararg perfis: String, ambiente: Map<String, Any> = emptyMap()): MockMvc {
        val context = contexto(propriedades, *perfis, ambiente = ambiente)
        return MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter::class.java))
            .build()
    }

    @Test
    fun `desligada por padrao a API continua exigindo token`() {
        val mvc = mvc(emptyMap())
        mvc.perform(post("/integracoes/erp/consultas")).andExpect(status().isUnauthorized)
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        mvc.perform(get("/test/protegido")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `explicitamente false continua exigindo token`() {
        mvc(mapOf("nota-agil.seguranca.desativada" to "false", "NOTA_AGIL_SECURITY_DISABLED" to "true"))
            .perform(post("/integracoes/erp/consultas")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `ligada aceita requisicao sem token com principal fixo e loga so metodo e caminho`(output: CapturedOutput) {
        val mvc = mvc(mapOf("nota-agil.seguranca.desativada" to "true"))
        mvc.perform(post("/integracoes/erp/consultas?segredo=valor-nao-logado")
            .contentType(MediaType.APPLICATION_JSON).content("""{"cnpjPrestador":"dado-nao-logado"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.usuario").value(SegurancaDesativada.PRINCIPAL))
            .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("ROLE_admin")))
        mvc.perform(get("/test/protegido")).andExpect(status().isOk)
            .andExpect(jsonPath("$.usuario").value(SegurancaDesativada.PRINCIPAL))
        assertTrue(output.out.contains("SEGURANCA DESATIVADA (NOTA_AGIL_SECURITY_DISABLED)"))
        assertTrue(output.out.contains("SEGURANCA DESATIVADA: POST /integracoes/erp/consultas aceita sem autenticacao"))
        assertFalse(output.out.contains("valor-nao-logado"))
        assertFalse(output.out.contains("dado-nao-logado"))
    }

    @Test
    fun `variavel NOTA_AGIL_SECURITY_DISABLED liga a chave`() {
        mvc(mapOf("NOTA_AGIL_SECURITY_DISABLED" to "true"))
            .perform(post("/integracoes/erp/consultas")).andExpect(status().isOk)
            .andExpect(jsonPath("$.usuario").value(SegurancaDesativada.PRINCIPAL))
    }

    @Test
    fun `variavel de ambiente real NOTA_AGIL_SECURITY_DISABLED liga a chave`() {
        mvc(emptyMap(), ambiente = mapOf("NOTA_AGIL_SECURITY_DISABLED" to "true"))
            .perform(post("/integracoes/erp/consultas")).andExpect(status().isOk)
            .andExpect(jsonPath("$.usuario").value(SegurancaDesativada.PRINCIPAL))
    }

    @Test
    fun `SEGURANCA_DESATIVADA generico nao liga a chave`() {
        val context = contexto(emptyMap(), ambiente = mapOf("SEGURANCA_DESATIVADA" to "true"))
        // O relaxed binding esta ativo (o nome antigo seria resolvido), mas a chave continua desligada.
        assertEquals("true", context.environment.getProperty("seguranca.desativada"))
        assertFalse(context.getBean(SegurancaDesativada::class.java).ativa)
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter::class.java))
            .build()
            .perform(post("/integracoes/erp/consultas")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `propriedade generica seguranca_desativada nao liga a chave`() {
        mvc(mapOf("seguranca.desativada" to "true"))
            .perform(post("/integracoes/erp/consultas")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `ligada um Bearer invalido continua recusado`() {
        mvc(mapOf("nota-agil.seguranca.desativada" to "true"))
            .perform(post("/integracoes/erp/consultas").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-jwt"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `ligada com profile de producao a aplicacao nao sobe`() {
        listOf("prd", "prod", "production", "PRD").forEach { perfil ->
            val erro = assertThrows<Exception> { contexto(mapOf("NOTA_AGIL_SECURITY_DISABLED" to "true"), "hmg", perfil) }
            val causas = generateSequence<Throwable>(erro) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            assertTrue(causas.contains("nao e permitido com o profile [$perfil]"), causas)
        }
    }

    @Test
    fun `ligada com profile padrao de producao a aplicacao nao sobe`() {
        // environment.setDefaultProfiles e SPRING_PROFILES_DEFAULT (spring.profiles.default)
        val viaApi = assertThrows<Exception> {
            contexto(mapOf("NOTA_AGIL_SECURITY_DISABLED" to "true"), perfisPadrao = listOf("prd"))
        }
        val viaVariavel = assertThrows<Exception> {
            contexto(emptyMap(), ambiente = mapOf("NOTA_AGIL_SECURITY_DISABLED" to "true", "SPRING_PROFILES_DEFAULT" to "prd"))
        }
        listOf(viaApi, viaVariavel).forEach { erro ->
            val causas = generateSequence<Throwable>(erro) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            assertTrue(causas.contains("nao e permitido com o profile [prd]"), causas)
        }
    }

    @Test
    fun `desligada com profile de producao sobe normalmente`() {
        mvc(emptyMap(), "prd").perform(post("/integracoes/erp/consultas")).andExpect(status().isUnauthorized)
    }

    @Configuration
    @EnableWebMvc
    class MvcConfig

    @RestController
    class Rotas {
        private fun quem(auth: Authentication) = mapOf("usuario" to auth.name, "roles" to auth.authorities.map { it.authority })

        @PostMapping("/integracoes/erp/consultas")
        fun consultas(auth: Authentication) = quem(auth)

        @GetMapping("/test/protegido")
        fun protegido(auth: Authentication) = quem(auth)
    }
}
