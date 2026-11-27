package br.andrew.nota.agil.erp.sap

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

class SapConsultaErpClientWiringTest {

    @Configuration
    @EnableConfigurationProperties
    @Import(SapContasPagarProperties::class, SapRovemaTokenProvider::class, SapConsultaErpClient::class)
    class Beans {
        // Outro RestClient no contexto (como Qive/SoftExpert) nao pode ser injetado no cliente SAP.
        @Bean fun outroRestClient(): RestClient = RestClient.create("https://outro.example.invalid")
    }

    private val runner = ApplicationContextRunner().withUserConfiguration(Beans::class.java)

    @Test
    fun `sem propriedades o SAP fica desabilitado`() {
        runner.run { ctx ->
            assertFalse(ctx.getBean(SapConsultaErpClient::class.java).consultaPagamentoImplementada)
        }
    }

    @Test
    fun `com URL, token e client configurados o SAP fica habilitado`() {
        runner.withPropertyValues(
            "integracao.erp.sap.url=https://sap-rovema.example.invalid",
            "integracao.erp.sap.token-url=https://sso.example.invalid/token",
            "integracao.erp.sap.client-id=nota-agil-teste",
            "integracao.erp.sap.client-secret=segredo-de-teste",
        ).run { ctx ->
            assertTrue(ctx.getBean(SapConsultaErpClient::class.java).consultaPagamentoImplementada)
        }
    }
}
