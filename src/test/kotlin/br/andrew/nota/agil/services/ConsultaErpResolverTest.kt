package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.*
import br.andrew.nota.agil.erp.sap.SapConsultaErpClient
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.repository.CompanyRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class ConsultaErpResolverTest {
    private val companies = mock(CompanyRepository::class.java)
    private val sap = SapConsultaErpClient()
    private val dealer = DealerConsultaErpClient()
    private val config = ConsultaErpConfiguration().apply { cnpjs = mapOf("12345678000190" to Erp.DEALER) }
    private val resolver = ConsultaErpResolver(config, listOf(sap, dealer), companies)

    @Test
    fun `deve consultar vinculo atualizado sem reiniciar e respeitar nenhum ERP`() {
        `when`(companies.findById("12345678000190"))
            .thenReturn(Optional.of(Company("12345678000190", "Teste", Erp.SAP)))
            .thenReturn(Optional.of(Company("12345678000190", "Teste", Erp.DEALER)))
            .thenReturn(Optional.of(Company("12345678000190", "Teste", null)))
        assertSame(sap, resolver.resolver("12.345.678/0001-90"))
        assertSame(dealer, resolver.resolver("12345678000190"))
        assertNull(resolver.resolver("12345678000190"))
    }

    @Test
    fun `configuracao legada deve valer apenas quando empresa nao estiver cadastrada`() {
        `when`(companies.findById(anyString())).thenReturn(Optional.empty())
        assertSame(dealer, resolver.resolver("12345678000190"))
        assertNull(resolver.resolver("98765432000100"))
    }
}
