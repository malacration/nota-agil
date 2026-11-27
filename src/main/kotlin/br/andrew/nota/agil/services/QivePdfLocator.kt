package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * PDF de nota importada de ERP no Qive.
 *
 * - NFe/CTe com chave de 44: DANFE/DACTE direto pela chave.
 * - Sem chave (NFS-e do SAP, notas do Dealer, que nao informa chave nem tipo): procura a nota
 *   recebida pelo tomador no Qive, numa janela de datas de recebimento a partir da emissao, e casa
 *   pelo CNPJ/CPF do prestador + numero ([NumeroNota]: 9 ultimos digitos so em NFS-e e so longo x
 *   curto) + serie (NFe/CTe) ou chave fiscal (NFS-e) quando os dois lados tem. Tipo desconhecido procura em
 *   NFe, CTe e NFS-e. Mais de uma nota possivel (em qualquer tipo) = ambiguo = sem PDF: anexar a nota
 *   errada no SE e pior que seguir sem anexo. Numero igual vence o casamento pelos 9 ultimos.
 *
 * Qualquer falha devolve null (quem chama decide o proximo passo). Nao loga CNPJ, chave nem numero.
 */
@Component
class QivePdfLocator(private val qive: QiveApiClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun buscar(duplicata: Duplicata): ByteArray? = try {
        porChave(duplicata) ?: porBusca(duplicata)
    } catch (e: Exception) {
        log.info("PDF da nota nao obtido no Qive ({})", e.javaClass.simpleName)
        null
    }

    private fun porChave(d: Duplicata): ByteArray? {
        val chave = d.chaveAcesso?.takeIf { CHAVE_44.matches(it) } ?: return null
        return runCatching {
            when (d.tipo) {
                TipoDuplicata.Nfe -> qive.nfe.danfe(chave)
                TipoDuplicata.Cte -> qive.cte.dacte(chave)
                else -> null
            }?.data?.decodePdf()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    internal data class Candidata(val tipo: TipoDuplicata, val id: String, val exata: Boolean)

    private fun porBusca(d: Duplicata): ByteArray? {
        val tomador = d.cpfCnpjTomador.filter(Char::isDigit).takeIf { it.length == 14 } ?: return null
        val inicio = d.dataEmissao.toInstant().minus(2, ChronoUnit.DAYS)
        val fim = minOf(d.dataEmissao.toInstant().plus(JANELA_DIAS, ChronoUnit.DAYS), Instant.now())
        if (fim.isBefore(inicio)) return null
        val tipos = if (d.tipo == TipoDuplicata.NaoDetectado) listOf(TipoDuplicata.Nfe, TipoDuplicata.Cte, TipoDuplicata.Nfse) else listOf(d.tipo)
        val candidatas = tipos.flatMap {
            // Busca incompleta (passou de MAX_PAGINAS) e indeterminada: a outra nota possivel pode estar
            // na pagina seguinte. Nao anexa nada.
            procurar(it, tomador, Date.from(inicio), Date.from(fim), d) ?: run {
                log.info("PDF da nota nao anexado: busca no Qive passou de {} paginas", MAX_PAGINAS)
                return null
            }
        }.distinct()
        val escolhida = escolher(candidatas) ?: run {
            if (candidatas.size > 1) log.info("PDF da nota nao anexado: {} notas possiveis no Qive (ambiguo)", candidatas.size)
            return null
        }
        return when (escolhida.tipo) {
            TipoDuplicata.Nfe -> qive.nfe.danfe(escolhida.id)
            TipoDuplicata.Cte -> qive.cte.dacte(escolhida.id)
            TipoDuplicata.Nfse -> qive.nfse.danfse(escolhida.id)
            else -> null
        }?.data?.decodePdf()?.takeIf { it.isNotEmpty() }
    }

    /** Notas do mesmo prestador + numero recebidas pelo tomador na janela; null = busca incompleta. */
    private fun procurar(tipo: TipoDuplicata, tomador: String, de: Date, ate: Date, alvo: Duplicata): List<Candidata>? {
        if (tipo != TipoDuplicata.Nfe && tipo != TipoDuplicata.Cte && tipo != TipoDuplicata.Nfse) return emptyList()
        return paginar(MAX_PAGINAS) { cursor ->
            val pagina: ReceivedResponse<out Any> = when (tipo) {
                TipoDuplicata.Nfe -> qive.nfe.listReceived(listOf(tomador), cursor, de, ate)
                TipoDuplicata.Cte -> qive.cte.listReceived(listOf(tomador), cursor, de, ate)
                else -> qive.nfse.listReceived(listOf(tomador), cursor, de, ate)
            }
            val proximo = if (pagina.page?.isEnd() ?: true) null else pagina.page!!.getNextCursor()
            pagina.data.mapNotNull { casa(tipo, it, alvo) } to proximo
        }
    }

    private fun casa(tipo: TipoDuplicata, item: Any, alvo: Duplicata): Candidata? {
        val (id, duplicata) = runCatching {
            when (item) {
                is br.andrew.nota.agil.qive.model.NotaProdutos -> item.id to item.getDuplicata()
                is br.andrew.nota.agil.qive.model.ConhecimentoTransporte -> item.id to item.getDuplicata()
                is br.andrew.nota.agil.qive.model.NotaServico -> item.id to item.getDuplicata()
                else -> null
            }
        }.getOrNull() ?: return null
        return if (confere(duplicata, alvo, tipo)) Candidata(tipo, id, NumeroNota.limpo(duplicata.numero) == NumeroNota.limpo(alvo.numero)) else null
    }

    companion object {
        private val CHAVE_44 = Regex("[0-9]{44}")
        const val JANELA_DIAS = 20L
        const val MAX_PAGINAS = 20

        /**
         * A nota [candidata] do Qive (do [tipo] consultado) pode ser o [alvo]? Prestador igual, numero
         * por [NumeroNota.mesmo] (9 ultimos so em NFS-e) e, quando os dois lados tem, serie igual
         * (NFe/CTe) ou chave fiscal igual (NFS-e).
         */
        internal fun confere(candidata: Duplicata, alvo: Duplicata, tipo: TipoDuplicata): Boolean {
            if (candidata.cpfCnpjPrestador.filter(Char::isDigit) != alvo.cpfCnpjPrestador.filter(Char::isDigit)) return false
            if (!NumeroNota.mesmo(candidata.numero, alvo.numero, tipo == TipoDuplicata.Nfse)) return false
            if (tipo == TipoDuplicata.Nfse) {
                // NFS-e: a serie do RPS/DPS nem sempre e a gravada no ERP; a identidade e a chave fiscal.
                val chaveA = candidata.chaveFiscal?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                val chaveB = alvo.chaveFiscal?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                return chaveA == null || chaveB == null || chaveA == chaveB
            }
            val serieA = NumeroNota.serie(candidata.serieFiscal())
            val serieB = NumeroNota.serie(alvo.serieFiscal())
            return serieA == null || serieB == null || serieA == serieB
        }

        /**
         * Le ate [maxPaginas] paginas ([pagina] devolve as candidatas e o proximo cursor, null no fim).
         * Se ainda houver pagina depois do limite, devolve null: o resultado parcial nao prova que a
         * candidata e unica.
         */
        internal fun paginar(maxPaginas: Int, pagina: (Int) -> Pair<List<Candidata>, Int?>): List<Candidata>? {
            val achadas = mutableListOf<Candidata>()
            var cursor = 0
            repeat(maxPaginas) {
                val (itens, proximo) = pagina(cursor)
                achadas += itens
                cursor = proximo ?: return achadas
            }
            return null
        }

        /** Uma so candidata com numero igual; senao uma so pelos 9 ultimos; senao null (nenhuma ou ambiguo). */
        internal fun escolher(candidatas: List<Candidata>): Candidata? {
            val exatas = candidatas.filter { it.exata }
            if (exatas.isNotEmpty()) return exatas.singleOrNull()
            return candidatas.singleOrNull()
        }

    }
}
