package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.services.QivePdfLocator.Candidata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Date

/** Dados ficticios. */
class QivePdfLocatorTest {

    private fun duplicata(numero: String, tipo: TipoDuplicata, serie: String? = null, chaveFiscal: String? = null, prestador: String = "98765432000100") =
        Duplicata(Date(), prestador, "F", "12345678000190", "E", BigDecimal.ONE, BigDecimal.ONE, numero, tipo,
            serie = serie, chaveFiscal = chaveFiscal)

    @Test
    fun `numero igual sem zeros a esquerda e NFS-e longa x curta pelos 9 ultimos`() {
        assertTrue(NumeroNota.mesmo("000123", "123", nfse = false))
        assertTrue(NumeroNota.mesmo("2600000001234", "1234", nfse = true))
        assertTrue(NumeroNota.mesmo("1234", "2600000001234", nfse = true))
        assertFalse(NumeroNota.mesmo("2600000001234", "1234", nfse = false), "9 ultimos so em NFS-e")
        assertFalse(NumeroNota.mesmo("123", "1234", nfse = true))
        assertFalse(NumeroNota.mesmo("0", "0", nfse = true))
        assertFalse(NumeroNota.mesmo("", "1", nfse = true))
    }

    @Test
    fun `dois numeros longos distintos com o mesmo sufixo nao sao a mesma nota`() {
        assertFalse(NumeroNota.mesmo("2025000001234", "2026000001234", nfse = true))
        assertTrue(NumeroNota.mesmo("2026000001234", "02026000001234", nfse = true))
        assertFalse(QivePdfLocator.confere(duplicata("2025000001234", TipoDuplicata.Nfse),
            duplicata("2026000001234", TipoDuplicata.Nfse), TipoDuplicata.Nfse))
    }

    @Test
    fun `confere prestador, serie de NFe e chave fiscal de NFS-e quando os dois lados tem`() {
        val alvo = duplicata("123", TipoDuplicata.Nfe, serie = "1")
        assertTrue(QivePdfLocator.confere(duplicata("123", TipoDuplicata.Nfe, serie = "001"), alvo, TipoDuplicata.Nfe))
        assertTrue(QivePdfLocator.confere(duplicata("123", TipoDuplicata.Nfe), alvo, TipoDuplicata.Nfe), "serie desconhecida")
        assertFalse(QivePdfLocator.confere(duplicata("123", TipoDuplicata.Nfe, serie = "2"), alvo, TipoDuplicata.Nfe))
        assertFalse(QivePdfLocator.confere(duplicata("123", TipoDuplicata.Nfe, prestador = "11111111000111"), alvo, TipoDuplicata.Nfe))

        val chave = "1".repeat(50)
        val nfse = duplicata("1234", TipoDuplicata.Nfse, chaveFiscal = chave)
        assertTrue(QivePdfLocator.confere(duplicata("2600000001234", TipoDuplicata.Nfse, chaveFiscal = chave), nfse, TipoDuplicata.Nfse))
        assertFalse(QivePdfLocator.confere(duplicata("2600000001234", TipoDuplicata.Nfse, chaveFiscal = "2".repeat(50)), nfse, TipoDuplicata.Nfse))
    }

    @Test
    fun `mais de uma nota possivel no Qive e ambiguo e nao anexa nada`() {
        val exata = Candidata(TipoDuplicata.Nfse, "q1", exata = true)
        val sufixo1 = Candidata(TipoDuplicata.Nfse, "q2", exata = false)
        val sufixo2 = Candidata(TipoDuplicata.Nfse, "q3", exata = false)
        assertEquals(exata, QivePdfLocator.escolher(listOf(sufixo1, exata)), "numero igual vence o sufixo")
        assertEquals(sufixo1, QivePdfLocator.escolher(listOf(sufixo1)))
        assertNull(QivePdfLocator.escolher(listOf(sufixo1, sufixo2)), "dois longos com o mesmo sufixo")
        assertNull(QivePdfLocator.escolher(listOf(exata, Candidata(TipoDuplicata.Nfe, "q4", exata = true))), "tipos diferentes")
        assertNull(QivePdfLocator.escolher(emptyList()))
    }

    @Test
    fun `regex de candidatos acha zeros a esquerda e NFS-e longa nos dois sentidos`() {
        val curto = Regex(NumeroNota.regexCandidatos(listOf("1234"))!!)
        assertTrue(curto.matches("0001234"))
        assertTrue(curto.matches("2600000001234"))
        assertFalse(curto.matches("12345"))
        val longo = Regex(NumeroNota.regexCandidatos(listOf("2600000001234"))!!)
        assertTrue(longo.matches("1234"))
        assertTrue(longo.matches("000001234"))
        assertTrue(longo.matches("02600000001234"))
        assertFalse(longo.matches("123"))
        assertNull(NumeroNota.regexCandidatos(listOf("000", "")))
        assertTrue(curto.matches("1.234"))
        assertTrue(curto.matches(" 001234 "))
        assertTrue(longo.matches("2.600.000.001.234"))
        val doc = Regex(NumeroNota.regexDocumentos(listOf("98.765.432/0001-00", "12345678901"))!!)
        for (gravado in listOf("98765432000100", "98.765.432/0001-00", " 98.765.432/0001-00 ", "98765432/000100", "123.456.789-01"))
            assertTrue(doc.matches(gravado), gravado)
        assertFalse(doc.matches("98765432000101"))
        assertFalse(doc.matches("198765432000100"))
        assertNull(NumeroNota.regexDocumentos(listOf("", "./-")))
    }

    @Test
    fun `busca que passa do limite de paginas e indeterminada e nao escolhe a candidata parcial`() {
        val naPagina2 = Candidata(TipoDuplicata.Nfse, "q20", exata = true)
        // 3 paginas lidas (limite), mas ainda ha a 4a: a outra nota possivel poderia estar la.
        val incompleta = QivePdfLocator.paginar(3) { cursor ->
            (if (cursor == 1) listOf(naPagina2) else emptyList<Candidata>()) to cursor + 1
        }
        assertNull(incompleta)
        // Mesmo volume terminando na ultima pagina permitida: completa.
        val completa = QivePdfLocator.paginar(3) { cursor ->
            (if (cursor == 1) listOf(naPagina2) else emptyList<Candidata>()) to (cursor + 1).takeIf { it < 3 }
        }
        assertEquals(listOf(naPagina2), completa)
    }
}
