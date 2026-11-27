package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.dealer.DealerleniumClient
import br.andrew.nota.agil.erp.sap.SapNotasEntradaClient
import br.andrew.nota.agil.infrastructure.security.Perfil
import br.andrew.nota.agil.infrastructure.security.Perfis
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.Erp
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** `nota-agil.filiais-usuario.cache-ttl` (padrao 12 h): validade das filiais de cada usuario. */
@Component
@ConfigurationProperties(prefix = "nota-agil.filiais-usuario")
class FiliaisUsuarioProperties {
    var cacheTtl: Duration = Duration.ofHours(12)
    /** Mapa filial -> CNPJ de cada ERP (igual para todos os usuarios). */
    var catalogoTtl: Duration = Duration.ofHours(12)
}

/**
 * Filiais liberadas a um operador em um ERP.
 * - [cnpjs]: CNPJs das filiais liberadas (SAP: so quando TODAS as filiais do SAP com aquele CNPJ
 *   estao liberadas, porque o sap-rovema consulta pelo CNPJ; Dealer: os das empresas liberadas);
 * - [codigosDealer]: so Dealer, codigos (Titulo_EmpresaCod) liberados por CNPJ. A consulta ao Dealer
 *   vai pelo codigo da empresa cadastrada, entao e ele que precisa estar liberado, nao so o CNPJ.
 */
data class FiliaisPermitidas(val cnpjs: Set<String>, val codigosDealer: Map<String, Set<Int>> = emptyMap()) {
    fun permite(erp: Erp, empresa: Company): Boolean = when (erp) {
        Erp.SAP -> empresa.cnpj in cnpjs
        Erp.DEALER -> empresa.codigoDealer?.let { it in codigosDealer[empresa.cnpj].orEmpty() } ?: false
    }
}

/**
 * Filiais que cada usuario pode usar na importacao de notas, por ERP.
 *
 * - admin: todas (sem consulta);
 * - operador: as que o proprio ERP libera para ele, consultadas com o token do usuario:
 *   SAP -> sap-rovema `GET /branch` (BPLIds; regra do SAP: vendedor, colaborador, super vendedor),
 *   convertidos em CNPJ pelas filiais do SAP (`/contas-pagar/filiais`). CNPJ com mais de uma filial
 *   (BPLId) no SAP so e liberado se o usuario tiver todas elas (a consulta e pelo CNPJ);
 *   Dealer -> dealerlenium `GET /usuario/empresas` (ids das roles), convertidos em codigo da empresa
 *   (Titulo_EmpresaCod) + CNPJ pelas empresas do dealerlenium (`/api/contas-pagar/empresas`); a
 *   empresa cadastrada so e liberada se o CODIGO dela estiver liberado (dois codigos podem ter o
 *   mesmo CNPJ).
 *
 * Cache em memoria por usuario e ERP ([FiliaisUsuarioProperties.cacheTtl]). Falha NAO e cacheada
 * (o proximo acesso tenta de novo). O admin invalida tudo ou um usuario; o logout do front limpa o
 * cache do proprio usuario. Uma consulta que estava em andamento durante uma invalidacao NAO grava
 * no cache (geracao): a permissao revogada nao volta pelo resultado antigo. Nao loga CNPJ, token
 * nem BPLId.
 */
@Service
class FiliaisUsuarioService(
    private val sap: SapNotasEntradaClient,
    private val dealer: DealerleniumClient,
    private val properties: FiliaisUsuarioProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<Pair<String, Erp>, Entrada>()
    private val catalogos = ConcurrentHashMap<Erp, Pair<List<FilialCatalogo>, Instant>>()
    /** Incrementada a cada invalidacao; resultado carregado em outra geracao nao entra no cache. */
    private var geracao = 0L
    private val trava = Any()

    data class Entrada(val permitidas: FiliaisPermitidas, val carregadoEm: Instant)

    /** Filial do catalogo do ERP: [chave] = BPLId (SAP) ou id da role (Dealer). */
    internal data class FilialCatalogo(val chave: String, val cnpj: String, val codigoDealer: Int? = null)

    /** Filiais liberadas ao usuario no [erp]; null = sem restricao (admin). */
    fun permitidas(auth: Authentication?, erp: Erp): FiliaisPermitidas? {
        when (Perfis.de(auth)) {
            Perfil.ADMIN -> return null
            Perfil.OPERADOR -> Unit
            else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil sem acesso as filiais")
        }
        val login = auth!!.name
        val chave = login to erp
        cache[chave]?.takeIf { Duration.between(it.carregadoEm, clock.instant()) < properties.cacheTtl }?.let { return it.permitidas }
        val token = auth.credentials as? String
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Sessao sem token para consultar suas filiais; entre novamente")
        val inicio = geracaoAtual()
        val permitidas = carregar(erp, token, inicio)
        synchronized(trava) {
            if (geracao == inicio) cache[chave] = Entrada(permitidas, clock.instant())
        }
        log.info("Filiais do usuario carregadas: erp={} quantidade={}", erp, permitidas.cnpjs.size)
        return permitidas
    }

    /** CNPJs liberados ao usuario no [erp]; null = sem restricao (admin). */
    fun cnpjsPermitidos(auth: Authentication?, erp: Erp): Set<String>? = permitidas(auth, erp)?.cnpjs

    /** 403 se a [empresa] (CNPJ e, no Dealer, o codigo) nao estiver liberada ao usuario no [erp]. */
    fun exigirPermitido(auth: Authentication?, erp: Erp, empresa: Company) {
        val permitidas = permitidas(auth, erp) ?: return
        if (!permitidas.permite(erp, empresa)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Filial nao liberada para voce no ${erp.name}")
    }

    private fun carregar(erp: Erp, token: String, inicio: Long): FiliaisPermitidas = when (erp) {
        Erp.SAP -> {
            val catalogo = catalogo(Erp.SAP, inicio) {
                sap.filiais().mapNotNull { f -> cnpj14(f.cnpj)?.let { FilialCatalogo(f.bplId.toString(), it) } }
            }
            val meus = sap.filiaisDoUsuario(token).map(Int::toString).toSet()
            val porCnpj = catalogo.groupBy { it.cnpj }.filterValues { fs -> fs.any { it.chave in meus } }
            val completos = porCnpj.filterValues { fs -> fs.all { it.chave in meus } }.keys
            if (completos.size < porCnpj.size)
                log.warn("Filiais do usuario no SAP: {} CNPJ(s) com outra filial nao liberada foram recusados", porCnpj.size - completos.size)
            FiliaisPermitidas(completos)
        }
        Erp.DEALER -> {
            val catalogo = catalogo(Erp.DEALER, inicio) {
                dealer.empresas().mapNotNull { e ->
                    val id = e.id?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    cnpj14(e.cnpj)?.takeIf { e.codigoDealer > 0 }?.let { FilialCatalogo(id, it, e.codigoDealer) }
                }
            }
            val meus = dealer.empresasDoUsuario(token).toSet()
            val codigos = catalogo.filter { it.chave in meus }
                .groupBy({ it.cnpj }, { it.codigoDealer!! }).mapValues { it.value.toSet() }
            FiliaisPermitidas(codigos.keys, codigos)
        }
    }

    private fun cnpj14(cnpj: String?): String? = cnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }

    private fun catalogo(erp: Erp, inicio: Long, carregar: () -> List<FilialCatalogo>): List<FilialCatalogo> {
        catalogos[erp]?.takeIf { Duration.between(it.second, clock.instant()) < properties.catalogoTtl }?.let { return it.first }
        val lista = carregar()
        synchronized(trava) {
            if (geracao == inicio) catalogos[erp] = lista to clock.instant()
        }
        return lista
    }

    private fun geracaoAtual(): Long = synchronized(trava) { geracao }

    // ---------------------------------------------------------------- administracao do cache

    fun entradas(): List<EntradaCacheFiliais> = cache.entries
        .map { (chave, e) ->
            EntradaCacheFiliais(chave.first, chave.second, e.permitidas.cnpjs.size, e.carregadoEm, e.carregadoEm.plus(properties.cacheTtl))
        }
        .sortedWith(compareBy({ it.login }, { it.erp }))

    /** Limpa o cache do [login] (todas as ERPs); devolve quantas entradas sairam. */
    fun invalidarUsuario(login: String): Int = synchronized(trava) {
        geracao++
        val chaves = cache.keys.filter { it.first == login }
        chaves.forEach { cache.remove(it) }
        chaves.size
    }

    /** Limpa tudo, inclusive o mapa filial -> CNPJ. */
    fun invalidarTudo(): Int {
        val n = synchronized(trava) {
            geracao++
            val n = cache.size
            cache.clear()
            catalogos.clear()
            n
        }
        log.info("Cache de filiais por usuario invalidado: {} entradas", n)
        return n
    }

    fun ttl(): Duration = properties.cacheTtl
}

data class EntradaCacheFiliais(val login: String, val erp: Erp, val filiais: Int, val carregadoEm: Instant, val expiraEm: Instant)
