package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.interfaces.Status
import br.andrew.nota.agil.qive.interfaces.controllers.Company
import br.andrew.nota.agil.qive.interfaces.controllers.Cte
import br.andrew.nota.agil.qive.interfaces.controllers.Events
import br.andrew.nota.agil.qive.interfaces.controllers.Nfe
import br.andrew.nota.agil.qive.interfaces.controllers.Nfse
import br.andrew.nota.agil.qive.model.NotaServico
import br.andrew.nota.agil.repository.TaskRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.Date

/** Dados ficticios: CNPJs, chaves e numeros nao correspondem a documentos reais. */
class DadosFiscaisNotaResolverTest {

    private val tasks = mock(TaskRepository::class.java)
    private val nfse = mock(Nfse::class.java)
    private val qive = QiveApiClient(mock(Nfe::class.java), mock(Events::class.java), mock(Company::class.java), nfse, mock(Cte::class.java))
    private val resolver = DadosFiscaisNotaResolver(tasks, qive)

    private val chave44 = "11261098765432000100550010000001231123456780"
    private val chave44OutroCodigo = "11261098765432000100550010000001231999999990"
    private val chave44Serie2 = "11261098765432000100550020000001231123456780"
    private val chave44Cte = "11261098765432000100570010000001231123456780"
    private val chave50 = "1100205" + "1" + "2" + PRESTADOR + "2600000001234" + "2611" + "123456789" + "0"

    private fun requisicao(chave: String? = null, serie: String? = null, tipo: TipoDuplicata? = null, numero: String = "123") =
        RegistrarConsultaErpRequest(
            workflowId = "FXP-NA000001", solicitacaoId = "s1", cpfCnpjTomador = TOMADOR, cnpjPrestador = PRESTADOR,
            numDocFiscal = numero, valorParcela = BigDecimal("10"), serieDocFiscal = serie, tipoDoc = tipo, chaveAcesso = chave,
        )

    private fun duplicata(tipo: TipoDuplicata, chaveAcesso: String?, serie: String? = null, chaveFiscal: String? = null,
                          numero: String = "123") =
        Duplicata(Date(), PRESTADOR, "P", TOMADOR, "T", BigDecimal.TEN, BigDecimal.TEN, numero, tipo, chaveAcesso, serie, chaveFiscal)

    private fun task(d: Duplicata, tipo: TaskTypes = TaskTypes.CreateTask) = Task("t-${d.hashCode()}", "n", TaskStatus.FINISHED, tipo, d)

    @Test
    fun `pedido completo nao consulta task nem Qive`() {
        val r = resolver.completar(requisicao(chave44, "1", TipoDuplicata.Nfe))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave44, "1", TipoDuplicata.Nfe, "REQUISICAO"), r)
        verify(tasks, never()).findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001")
    }

    @Test
    fun `NFe pela task do workflow - chave e serie da chave`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44))))
        val r = resolver.completar(requisicao())
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave44, "001", TipoDuplicata.Nfe, "CREATE_TASK"), r)
    }

    @Test
    fun `o que o SE mandou nunca e trocado`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44))))
        val r = resolver.completar(requisicao(serie = "1", tipo = TipoDuplicata.Nfe))
        assertEquals("1", r.serie, "serie do SE fica como veio, nao vira o \"001\" da chave")
        assertEquals(TipoDuplicata.Nfe, r.tipo)
        assertEquals(chave44, r.chaveAcesso)
        assertEquals("REQUISICAO+CREATE_TASK", r.origem)
    }

    @Test
    fun `task do workflow que nao confere com o pedido e ignorada`() {
        val outroPrestador = Duplicata(Date(), "11111111000111", "P", TOMADOR, "T", BigDecimal.TEN, BigDecimal.TEN, "123",
            TipoDuplicata.Nfe, chave44)
        val casos = listOf(
            "prestador" to Pair(outroPrestador, requisicao()),
            "numero" to Pair(duplicata(TipoDuplicata.Nfe, chave44, numero = "124"), requisicao()),
            "tipo" to Pair(duplicata(TipoDuplicata.Nfe, chave44), requisicao(tipo = TipoDuplicata.Cte)),
            "tipo desconhecido na task" to Pair(duplicata(TipoDuplicata.NaoDetectado, chave44), requisicao(tipo = TipoDuplicata.Nfe)),
            "serie" to Pair(duplicata(TipoDuplicata.Nfe, chave44), requisicao(serie = "2")),
            "serie NFS-e" to Pair(duplicata(TipoDuplicata.Nfse, "qive-id-1", "00002", chave50, "2600000001234"),
                requisicao(serie = "1", numero = "2600000001234")),
        )
        for ((caso, dados) in casos) {
            val (d, req) = dados
            `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001")).thenReturn(listOf(task(d)))
            assertEquals(DadosFiscaisNotaResolver.soDaRequisicao(req), resolver.completar(req), caso)
        }
    }

    @Test
    fun `task do workflow de NFS-e antiga com serie do Qive diferente do pedido e ignorada`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1"))))
        `when`(nfse.receivedPorId(listOf("qive-id-1"), "JSON")).thenReturn(resposta(notaQive("NFS$chave50", "00002")))
        val r = resolver.completar(requisicao(serie = "1"))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "1", null, "REQUISICAO"), r)
    }

    @Test
    fun `task do workflow ignorada cai na busca por numero de NFe`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Cte, chave44Cte))))
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44))))
        val r = resolver.completar(requisicao(serie = "1", tipo = TipoDuplicata.Nfe))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave44, "1", TipoDuplicata.Nfe, "REQUISICAO+TASK_NUMERO"), r)
    }

    @Test
    fun `task do workflow confere com zeros a esquerda, CNPJ com mascara e NFS-e pelos 9 ultimos`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1", "00001", chave50, "2600000001234"))))
        val req = requisicao(numero = "000001234", serie = "1").copy(cnpjPrestador = "98.765.432/0001-00")
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave50, "1", TipoDuplicata.Nfse, "REQUISICAO+CREATE_TASK"),
            resolver.completar(req))

        assertTrue(DadosFiscaisNotaResolver.mesmoNumero("2600000001234", "1234", nfse = true))
        assertTrue(DadosFiscaisNotaResolver.mesmoNumero("1234", "2600000001234", nfse = true))
        assertTrue(DadosFiscaisNotaResolver.mesmoNumero("000123", "123", nfse = false))
        assertFalse(DadosFiscaisNotaResolver.mesmoNumero("2600000001234", "1234", nfse = false), "9 ultimos so em NFS-e")
        assertFalse(DadosFiscaisNotaResolver.mesmoNumero("2600000001234", "2500000001234", nfse = true))
        assertFalse(DadosFiscaisNotaResolver.mesmoNumero("123456789", "23456789", nfse = true), "nenhum lado longo")
        assertFalse(DadosFiscaisNotaResolver.mesmoNumero("0", "000", nfse = false), "zero nao e numero")
    }

    @Test
    fun `duas tasks com o mesmo recordID nao valem e cai na busca por prestador e numero`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44)), task(duplicata(TipoDuplicata.Cte, "x"))))
        val d = duplicata(TipoDuplicata.Nfe, chave44)
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("000123", "123")))
            .thenReturn(listOf(task(d), task(d, TaskTypes.UploadPdf), task(d)))
        val r = resolver.completar(requisicao(numero = "000123", serie = "1", tipo = TipoDuplicata.Nfe))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave44, "1", TipoDuplicata.Nfe, "REQUISICAO+TASK_NUMERO"), r)
        assertTrue(r.completado)
    }

    @Test
    fun `busca por numero com notas diferentes da mesma serie nao escolhe nenhuma`() {
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44)), task(duplicata(TipoDuplicata.Nfe, chave44OutroCodigo))))
        val r = resolver.completar(requisicao(serie = "001", tipo = TipoDuplicata.Nfe))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "001", TipoDuplicata.Nfe, "REQUISICAO"), r)
    }

    @Test
    fun `sem tipo no pedido a busca por numero nao roda - CTe de mesmo numero nao vira tipo nem chave`() {
        // Cenario A da revisao: processo de NFS-e sem tipo/chave/serie e sem a propria task; o
        // mesmo prestador tem um CTe de mesmo numero com task. Desfecho seguro: DADOS_NOTA_INSUFICIENTES.
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Cte, chave44Cte))))
        for (tipo in listOf(null, TipoDuplicata.NaoDetectado)) {
            val r = resolver.completar(requisicao(tipo = tipo))
            assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, null, null, "NENHUMA"), r)
            assertFalse(r.completado)
        }
        verify(tasks, never()).findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123"))
        // Com serie, mas ainda sem tipo: tambem nao.
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "1", null, "REQUISICAO"), resolver.completar(requisicao(serie = "1")))
    }

    @Test
    fun `pedido NFS-e nao herda chave de CTe de mesmo numero`() {
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Cte, chave44Cte))))
        val r = resolver.completar(requisicao(serie = "1", tipo = TipoDuplicata.Nfse))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "1", TipoDuplicata.Nfse, "REQUISICAO"), r)
    }

    @Test
    fun `NFe sem serie no pedido nao herda chave nem serie de outra NFe de mesmo numero`() {
        // Cenario B da revisao: so a outra NFe (serie 002) tem task. Desfecho seguro: AMBIGUO no sap-rovema.
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44Serie2))))
        val r = resolver.completar(requisicao(tipo = TipoDuplicata.Nfe))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, null, TipoDuplicata.Nfe, "REQUISICAO"), r)
        verify(tasks, never()).findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123"))
    }

    @Test
    fun `busca por numero so usa a nota da mesma serie do pedido`() {
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44)), task(duplicata(TipoDuplicata.Nfe, chave44Serie2))))
        assertEquals(chave44Serie2, resolver.completar(requisicao(serie = "2", tipo = TipoDuplicata.Nfe)).chaveAcesso)
        assertEquals(chave44, resolver.completar(requisicao(serie = "001", tipo = TipoDuplicata.Nfe)).chaveAcesso)
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "3", TipoDuplicata.Nfe, "REQUISICAO"),
            resolver.completar(requisicao(serie = "3", tipo = TipoDuplicata.Nfe)), "nenhuma da mesma serie")
    }

    @Test
    fun `NFS-e nunca usa a busca por numero - nota antiga de mesmo numero e serie nao da a chave`() {
        // P3-1 da revisao: processo de NFS-e sem a propria task; o prestador tem NFS-e antiga de mesmo
        // numero e serie de RPS, com chave gravada na task (ou no Qive). Numero/serie de RPS nao sao
        // unicos como os da SEFAZ: nada e derivado.
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1", "00001", chave50))))
        `when`(nfse.receivedPorId(listOf("qive-id-1"), "JSON")).thenReturn(resposta(notaQive("NFS$chave50", "00001")))
        val r = resolver.completar(requisicao(serie = "1", tipo = TipoDuplicata.Nfse))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "1", TipoDuplicata.Nfse, "REQUISICAO"), r)
        verify(tasks, never()).findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123"))
        verify(nfse, never()).receivedPorId(listOf("qive-id-1"), "JSON")
    }

    @Test
    fun `busca por numero de NFe com candidata de serie desconhecida nao escolhe`() {
        val semChaveSemSerie = duplicata(TipoDuplicata.Nfe, null)
        `when`(tasks.findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(listOf(PRESTADOR), listOf("123")))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfe, chave44)), task(semChaveSemSerie)))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, "1", TipoDuplicata.Nfe, "REQUISICAO"),
            resolver.completar(requisicao(serie = "1", tipo = TipoDuplicata.Nfe)))
    }

    @Test
    fun `NFS-e nova usa a chave fiscal e a serie do RPS gravadas na task`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1", "00001", chave50, "2600000001234"))))
        val r = resolver.completar(requisicao(numero = "2600000001234"))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave50, "00001", TipoDuplicata.Nfse, "CREATE_TASK"), r)
        verify(nfse, never()).receivedPorId(listOf("qive-id-1"), "JSON")
    }

    @Test
    fun `NFS-e de task antiga le chave e serie no Qive uma vez so`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1"))))
        `when`(nfse.receivedPorId(listOf("qive-id-1"), "JSON")).thenReturn(resposta(notaQive("NFS$chave50", "00001")))
        val r = resolver.completar(requisicao())
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(chave50, "00001", TipoDuplicata.Nfse, "CREATE_TASK+QIVE"), r)
        resolver.completar(requisicao())
        verify(nfse, times(1)).receivedPorId(listOf("qive-id-1"), "JSON")
    }

    @Test
    fun `falha do Qive segue sem chave e sem serie`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001"))
            .thenReturn(listOf(task(duplicata(TipoDuplicata.Nfse, "qive-id-1"))))
        `when`(nfse.receivedPorId(listOf("qive-id-1"), "JSON")).thenThrow(RuntimeException("timeout"))
        val r = resolver.completar(requisicao())
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, null, TipoDuplicata.Nfse, "CREATE_TASK"), r)
    }

    @Test
    fun `falha do Mongo segue so com o que o SE mandou`() {
        `when`(tasks.findAllByTaskTypeAndRecordID(TaskTypes.CreateTask, "FXP-NA000001")).thenThrow(RuntimeException("mongo"))
        val r = resolver.completar(requisicao(tipo = TipoDuplicata.Cte))
        assertEquals(DadosFiscaisNotaResolver.DadosFiscais(null, null, TipoDuplicata.Cte, "REQUISICAO"), r)
        assertNull(r.chaveAcesso)
    }

    private fun resposta(nota: NotaServico) = ReceivedResponse(listOf(nota), null, 1, null, Status(200, "OK"))

    private fun notaQive(id: String, serie: String): NotaServico = jacksonObjectMapper().readValue(
        """{"id":"qive-id-1","xml":{"Nfse":{"InfNfse":{"@attributes":{"Id":"$id"},"Numero":"2600000001234",
           "DeclaracaoPrestacaoServico":{"InfDeclaracaoPrestacaoServico":{"Rps":{"IdentificacaoRps":{"Numero":"7","Serie":"$serie"}}}}}}}}""",
    )

    companion object {
        const val PRESTADOR = "98765432000100"
        const val TOMADOR = "12345678000190"
    }
}
