package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.notas.FilialErp
import br.andrew.nota.agil.erp.notas.FiltroNotasErp
import br.andrew.nota.agil.erp.notas.NotaEntradaErp
import br.andrew.nota.agil.erp.notas.NotasEntradaErpClient
import br.andrew.nota.agil.erp.notas.SituacaoNotasErp
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.CompanyChanged
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.CompanyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

/** Dados ficticios: CNPJs e codigos nao correspondem a registros reais. */
class FiliaisErpServiceTest {

    private class Fonte(override val erp: Erp, var filiais: List<FilialErp>, var configurado: Boolean = true) : NotasEntradaErpClient {
        override val nome = erp.name
        override fun situacao() = SituacaoNotasErp(true, configurado)
        override fun listar(empresa: Company, filtro: FiltroNotasErp) = emptyList<NotaEntradaErp>()
        override fun buscar(empresa: Company, ids: List<String>) = emptyList<NotaEntradaErp>()
        override fun filiais() = filiais
    }

    private val sap = Fonte(Erp.SAP, listOf(
        FilialErp("11111111000111", "Matriz"),
        FilialErp("11111111000111", "Matriz (outra BPL)"),
        FilialErp("22222222000122", "Filial sem Qive"),
    ))
    private val dealer = Fonte(Erp.DEALER, listOf(
        FilialErp("33333333000133", "Loja A", codigoDealer = 1),
        FilialErp("44444444000144", "Loja B", codigoDealer = 2),
        FilialErp("44444444000144", "Loja B2", codigoDealer = 3),
    ))
    private val repository = mock(CompanyRepository::class.java)
    private val qive = mock(QiveApiClient::class.java, RETURNS_DEEP_STUBS)
    private val events = mock(ApplicationEventPublisher::class.java)
    private val catalogo = mock(br.andrew.nota.agil.erp.CatalogoConsultaErp::class.java).also {
        `when`(it.disponiveis()).thenReturn(listOf(br.andrew.nota.agil.erp.ErpDisponivel(Erp.SAP, "SAP")))
    }
    private val service = FiliaisErpService(listOf(sap, dealer), repository, qive, events, catalogo)

    private fun qiveCom(vararg cnpjs: String) {
        `when`(qive.company.empresas().data).thenReturn(cnpjs.toList())
    }

    @Test
    fun `lista uma linha por CNPJ com situacao no cadastro e na Qive`() {
        qiveCom("11.111.111/0001-11")
        `when`(repository.findAll()).thenReturn(listOf(Company("11111111000111", "Ja", vinculadoQive = true)))
        val lista = service.listar(Erp.SAP)
        assertTrue(lista.qiveRespondeu)
        val porCnpj = lista.filiais.associateBy { it.cnpj }
        assertEquals(2, porCnpj.size)
        assertTrue(porCnpj.getValue("11111111000111").cadastrada)
        assertEquals(true, porCnpj.getValue("11111111000111").disponivelNoQive)
        assertEquals(false, porCnpj.getValue("22222222000122").disponivelNoQive)
        assertNull(porCnpj.getValue("22222222000122").vinculadoQive)
    }

    @Test
    fun `importa vinculando ao Qive so o CNPJ que existe la e publica a mudanca`() {
        qiveCom("11111111000111")
        `when`(repository.findAll()).thenReturn(emptyList())
        `when`(repository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty())

        val r = service.importar(Erp.SAP, listOf("11.111.111/0001-11", "22222222000122", "99999999000199")).associateBy { it.cnpj }

        assertEquals(StatusFilialErp.CRIADA, r.getValue("11111111000111").status)
        assertEquals(StatusFilialErp.CRIADA, r.getValue("22222222000122").status)
        assertEquals(StatusFilialErp.NAO_ENCONTRADA, r.getValue("99999999000199").status)
        verify(repository).save(Company("11111111000111", "Matriz", erpPagamento = Erp.SAP, vinculadoQive = true))
        verify(repository).save(Company("22222222000122", "Filial sem Qive", erpPagamento = Erp.SAP, vinculadoQive = false))
        verify(events).publishEvent(CompanyChanged(Company("22222222000122", "Filial sem Qive", erpPagamento = Erp.SAP, vinculadoQive = false)))
    }

    @Test
    fun `Qive fora do ar cadastra sem vinculo`() {
        `when`(qive.company.empresas()).thenThrow(RuntimeException("timeout"))
        `when`(repository.findAll()).thenReturn(emptyList())
        `when`(repository.findById("22222222000122")).thenReturn(Optional.empty())
        assertFalse(service.listar(Erp.SAP).qiveRespondeu)
        service.importar(Erp.SAP, listOf("22222222000122"))
        verify(repository).save(Company("22222222000122", "Filial sem Qive", erpPagamento = Erp.SAP, vinculadoQive = false))
    }

    @Test
    fun `Dealer preenche codigo em empresa existente e recusa CNPJ com dois codigos`() {
        qiveCom()
        val existente = Company("33333333000133", "Ja cadastrada", vinculadoQive = true)
        `when`(repository.findAll()).thenReturn(listOf(existente))
        `when`(repository.findById("33333333000133")).thenReturn(Optional.of(existente))

        val r = service.importar(Erp.DEALER, listOf("33333333000133", "44444444000144")).associateBy { it.cnpj }

        assertEquals(StatusFilialErp.ATUALIZADA, r.getValue("33333333000133").status)
        verify(repository).save(existente.copy(codigoDealer = 1))
        assertEquals(StatusFilialErp.ERRO, r.getValue("44444444000144").status)
        verify(repository, never()).findById("44444444000144")
    }

    @Test
    fun `SAP preenche ERP de pagamento vazio em empresa existente sem sobrescrever o preenchido`() {
        qiveCom()
        val semErp = Company("11111111000111", "Sem ERP")
        val comErp = Company("22222222000122", "Com ERP", erpPagamento = Erp.DEALER)
        `when`(repository.findAll()).thenReturn(listOf(semErp, comErp))
        `when`(repository.findById("11111111000111")).thenReturn(Optional.of(semErp))
        `when`(repository.findById("22222222000122")).thenReturn(Optional.of(comErp))
        val r = service.importar(Erp.SAP, listOf("11111111000111", "22222222000122")).associateBy { it.cnpj }
        assertEquals(StatusFilialErp.ATUALIZADA, r.getValue("11111111000111").status)
        verify(repository).save(semErp.copy(erpPagamento = Erp.SAP))
        assertEquals(StatusFilialErp.JA_CADASTRADA, r.getValue("22222222000122").status)
    }

    @Test
    fun `ERP sem conexao configurada e pedido vazio sao recusados`() {
        sap.configurado = false
        assertThrows<ResponseStatusException> { service.listar(Erp.SAP) }
        assertThrows<ResponseStatusException> { service.importar(Erp.DEALER, listOf("123")) }
    }
}
