package br.andrew.nota.agil.services

/**
 * Comparacao de numeros de nota entre fontes (Qive, SAP, Dealer).
 *
 * - Sempre: so digitos, sem zeros a esquerda ("000123" == "123").
 * - So NFS-e: o SAP grava apenas os 9 ultimos digitos dos numeros longos, entao um numero LONGO
 *   (mais de 9 digitos) casa com um CURTO (ate 9) igual aos 9 ultimos dele. Dois numeros longos so
 *   casam se forem iguais (sufixo igual nao basta: "2025000001234" != "2026000001234").
 */
object NumeroNota {
    const val DIGITOS_SAP = 9

    /** So digitos, sem zeros a esquerda; vazio = sem numero. */
    fun limpo(numero: String): String = numero.filter(Char::isDigit).trimStart('0')

    /**
     * Chave que agrupa os numeros que PODEM ser a mesma nota (longo -> 9 ultimos). Grossa de proposito:
     * so serve para travar/buscar candidatos; a decisao e sempre de [mesmo].
     */
    fun canonico(numero: String): String {
        val l = limpo(numero)
        return if (l.length > DIGITOS_SAP) l.takeLast(DIGITOS_SAP).trimStart('0') else l
    }

    fun mesmo(a: String, b: String, nfse: Boolean): Boolean {
        val x = limpo(a)
        val y = limpo(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (!nfse) return false
        val (longo, curto) = if (x.length > y.length) x to y else y to x
        return longo.length > DIGITOS_SAP && curto.length <= DIGITOS_SAP && longo.takeLast(DIGITOS_SAP).trimStart('0') == curto
    }

    /**
     * Regex (ancorada) de todo numero gravado que pode casar com algum dos [numeros]: o proprio numero
     * com zeros a esquerda, o curto dos 9 ultimos e qualquer longo com os mesmos 9 ultimos. Tolera
     * separadores no valor gravado ("1.234", " 1234"), como [limpo]. Usada junto com o prestador na
     * consulta, que fica limitada as notas dele; null se nenhum numero.
     */
    fun regexCandidatos(numeros: Collection<String>): String? {
        val alternativas = numeros.map(::limpo).filter(String::isNotEmpty).flatMap { l ->
            val c = canonico(l)
            if (c.isEmpty()) listOf("$ZEROS${digitos(l)}")
            else listOf("$ZEROS${digitos(l)}", "$ZEROS${digitos(c)}", "(?:[0-9]$SEP)*${digitos(c.padStart(DIGITOS_SAP, '0'))}")
        }.distinct()
        if (alternativas.isEmpty()) return null
        return "^$SEP(?:" + alternativas.joinToString("|") + ")$SEP$"
    }

    /** Serie sem zeros a esquerda ("001" == "1"), maiuscula; null se vazia. */
    fun serie(serie: String?): String? =
        serie?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.trimStart('0')?.ifEmpty { "0" }

    /**
     * Regex (ancorada) de CPF/CNPJ gravado com qualquer mascara, espacos ou separadores: os mesmos
     * digitos, na ordem, com nao-digitos opcionais entre eles e nas pontas ("12.345.678/0001-90 ",
     * "12345678/000190"). Quem usa compara de novo pelos digitos. Null se nenhum documento.
     */
    fun regexDocumentos(documentos: Collection<String>): String? {
        val alternativas = documentos.map { it.filter(Char::isDigit) }.filter(String::isNotEmpty).distinct().map(::digitos)
        if (alternativas.isEmpty()) return null
        return "^$SEP(?:" + alternativas.joinToString("|") + ")$SEP$"
    }

    private const val SEP = "[^0-9]*"
    private const val ZEROS = "(?:0$SEP)*"
    private fun digitos(d: String) = d.toCharArray().joinToString(SEP)
}
