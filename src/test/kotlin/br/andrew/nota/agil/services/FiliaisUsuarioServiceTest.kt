package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.dealer.DealerleniumClient
import br.andrew.nota.agil.erp.dealer.EmpresaDealerlenium
import br.andrew.nota.agil.erp.sap.FilialSapRovema
import br.andrew.nota.agil.erp.sap.SapNotasEntradaClient
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.Erp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Dados ficticios: CNPJs, logins e tokens nao correspondem a registros reais. */
class FiliaisUsuarioServiceTest {

    private class Relogio(var agora: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = agora
    }

    private val sap = mock(SapNotasEntradaClient::class.java)
    private val dealer = mock(DealerleniumClient::class.java)
    private val relogio = Relogio(Instant.parse("2026-11-25T12:00:00Z"))
    private val properties = FiliaisUsuarioProperties().apply { cacheTtl = Duration.ofHours(12) }
    private val service = FiliaisUsuarioService(sap, dealer, properties, relogio)

    private fun usuario(login: String, vararg roles: String, token: String? = "token-$login") =
        UsernamePasswordAuthenticationToken(login, token, roles.map { SimpleGrantedAuthority("ROLE_$it") })

    init {
        `when`(sap.filiais()).thenReturn(listOf(
            FilialSapRovema(2, "Matriz", "11111111000111"), FilialSapRovema(3, "Filial", "22.222.222/0001-22"),
            FilialSapRovema(4, "Mesmo CNPJ da matriz", "11111111000111"),
        ))
        `when`(dealer.empresas()).thenReturn(listOf(
            EmpresaDealerlenium(1, "33333333000133", "Loja A", "LOJA_A"), EmpresaDealerlenium(2, "44444444000144", "Loja B", "LOJA_B"),
        ))
    }

    @Test
    fun `admin ve tudo sem consultar ERP`() {
        assertNull(service.cnpjsPermitidos(usuario("adm", "admin"), Erp.SAP))
        verifyNoInteractions(sap, dealer)
    }

    @Test
    fun `operador SAP recebe os CNPJs das filiais que o sap-rovema libera, com cache`() {
        `when`(sap.filiaisDoUsuario("token-op")).thenReturn(listOf(3, 9))
        val op = usuario("op", "operador")
        assertEquals(setOf("22222222000122"), service.cnpjsPermitidos(op, Erp.SAP))
        assertEquals(setOf("22222222000122"), service.cnpjsPermitidos(op, Erp.SAP))
        verify(sap, times(1)).filiaisDoUsuario("token-op")
        assertDoesNotThrow { service.exigirPermitido(op, Erp.SAP, Company("22222222000122", "Filial")) }
        val erro = assertThrows<ResponseStatusException> { service.exigirPermitido(op, Erp.SAP, Company("11111111000111", "Matriz")) }
        assertEquals(HttpStatus.FORBIDDEN, erro.statusCode)
    }

    @Test
    fun `operador Dealer recebe os CNPJs das empresas das suas roles`() {
        `when`(dealer.empresasDoUsuario("token-op")).thenReturn(listOf("LOJA_B", "OUTRA"))
        assertEquals(setOf("44444444000144"), service.cnpjsPermitidos(usuario("op", "operador"), Erp.DEALER))
    }

    @Test
    fun `cache expira, e invalidado por usuario ou por inteiro`() {
        `when`(sap.filiaisDoUsuario("token-op")).thenReturn(listOf(2))
        `when`(sap.filiaisDoUsuario("token-op2")).thenReturn(listOf(3))
        val op = usuario("op", "operador")
        service.cnpjsPermitidos(op, Erp.SAP)
        service.cnpjsPermitidos(usuario("op2", "operador"), Erp.SAP)
        assertEquals(listOf("op", "op2"), service.entradas().map { it.login })

        assertEquals(1, service.invalidarUsuario("op"))
        assertEquals(listOf("op2"), service.entradas().map { it.login })
        service.cnpjsPermitidos(op, Erp.SAP)
        verify(sap, times(2)).filiaisDoUsuario("token-op")

        relogio.agora = relogio.agora.plus(Duration.ofHours(13))
        service.cnpjsPermitidos(op, Erp.SAP)
        verify(sap, times(3)).filiaisDoUsuario("token-op")

        assertEquals(2, service.invalidarTudo())
        assertEquals(emptyList<EntradaCacheFiliais>(), service.entradas())
    }

    @Test
    fun `falha do ERP nao e cacheada e sem perfil ou sem token e recusado`() {
        `when`(sap.filiaisDoUsuario("token-op"))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "O SAP recusou seu acesso"))
            .thenReturn(listOf(3))
        val op = usuario("op", "operador")
        assertThrows<ResponseStatusException> { service.cnpjsPermitidos(op, Erp.SAP) }
        assertEquals(setOf("22222222000122"), service.cnpjsPermitidos(op, Erp.SAP))

        assertThrows<ResponseStatusException> { service.cnpjsPermitidos(usuario("x", "outra"), Erp.SAP) }
        assertThrows<ResponseStatusException> { service.cnpjsPermitidos(usuario("y", "operador", token = null), Erp.SAP) }
    }

    @Test
    fun `SAP recusa CNPJ com outra filial nao liberada e libera quando o usuario tem todas`() {
        `when`(sap.filiaisDoUsuario("token-op")).thenReturn(listOf(2))
        `when`(sap.filiaisDoUsuario("token-op2")).thenReturn(listOf(2, 4))
        val op = usuario("op", "operador")
        assertEquals(emptySet<String>(), service.cnpjsPermitidos(op, Erp.SAP))
        assertThrows<ResponseStatusException> { service.exigirPermitido(op, Erp.SAP, Company("11111111000111", "Matriz")) }
        assertEquals(setOf("11111111000111"), service.cnpjsPermitidos(usuario("op2", "operador"), Erp.SAP))
    }

    @Test
    fun `Dealer com duas empresas no mesmo CNPJ libera so o codigo da role do usuario`() {
        `when`(dealer.empresas()).thenReturn(listOf(
            EmpresaDealerlenium(1, "55555555000155", "Loja C1", "LOJA_C1"), EmpresaDealerlenium(2, "55555555000155", "Loja C2", "LOJA_C2"),
        ))
        `when`(dealer.empresasDoUsuario("token-op")).thenReturn(listOf("LOJA_C1"))
        val op = usuario("op", "operador")
        assertEquals(setOf("55555555000155"), service.cnpjsPermitidos(op, Erp.DEALER))
        assertDoesNotThrow { service.exigirPermitido(op, Erp.DEALER, Company("55555555000155", "Loja C", codigoDealer = 1)) }
        // Mesmo CNPJ, mas a empresa cadastrada consulta o codigo 2, que o usuario nao tem.
        val erro = assertThrows<ResponseStatusException> {
            service.exigirPermitido(op, Erp.DEALER, Company("55555555000155", "Loja C", codigoDealer = 2))
        }
        assertEquals(HttpStatus.FORBIDDEN, erro.statusCode)
        assertThrows<ResponseStatusException> { service.exigirPermitido(op, Erp.DEALER, Company("55555555000155", "Loja C")) }
        // Admin segue sem restricao.
        assertDoesNotThrow { service.exigirPermitido(usuario("adm", "admin"), Erp.DEALER, Company("55555555000155", "Loja C", codigoDealer = 2)) }
    }

    @Test
    fun `invalidacao durante consulta em andamento nao deixa o resultado antigo no cache`() {
        val consultando = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        `when`(sap.filiaisDoUsuario("token-op")).thenAnswer {
            consultando.countDown()
            liberar.await(5, TimeUnit.SECONDS)
            listOf(3)
        }.thenReturn(emptyList())
        val op = usuario("op", "operador")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val antiga = executor.submit<Set<String>?> { service.cnpjsPermitidos(op, Erp.SAP) }
            assertTrue(consultando.await(5, TimeUnit.SECONDS))
            // Admin revoga e invalida enquanto a consulta antiga ainda esta em andamento.
            service.invalidarUsuario("op")
            liberar.countDown()
            assertEquals(setOf("22222222000122"), antiga.get(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
        assertEquals(emptyList<EntradaCacheFiliais>(), service.entradas())
        // Proximo acesso consulta de novo e ja ve a revogacao.
        assertEquals(emptySet<String>(), service.cnpjsPermitidos(op, Erp.SAP))
        verify(sap, times(2)).filiaisDoUsuario("token-op")
    }

    @Test
    fun `invalidar tudo durante consulta em andamento nao regrava o catalogo antigo`() {
        val consultando = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        `when`(sap.filiais()).thenAnswer {
            consultando.countDown()
            liberar.await(5, TimeUnit.SECONDS)
            listOf(FilialSapRovema(3, "Filial", "22222222000122"))
        }.thenReturn(listOf(FilialSapRovema(3, "Filial nova", "66666666000166")))
        `when`(sap.filiaisDoUsuario("token-op")).thenReturn(listOf(3))
        val op = usuario("op", "operador")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val antiga = executor.submit<Set<String>?> { service.cnpjsPermitidos(op, Erp.SAP) }
            assertTrue(consultando.await(5, TimeUnit.SECONDS))
            service.invalidarTudo()
            liberar.countDown()
            antiga.get(5, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
        assertEquals(emptyList<EntradaCacheFiliais>(), service.entradas())
        assertEquals(setOf("66666666000166"), service.cnpjsPermitidos(op, Erp.SAP))
        verify(sap, times(2)).filiais()
    }
}
