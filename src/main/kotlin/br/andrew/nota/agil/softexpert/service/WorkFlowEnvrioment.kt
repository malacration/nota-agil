package br.andrew.nota.agil.softexpert.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * `softexpert.workflow.*`.
 *
 * [campoSerie] / [campoChaveAcesso]: ID dos campos do formulario do processo (tabela [tableName])
 * que recebem a serie e a chave de acesso na criacao do processo (CreateTask). Vazios = nao
 * enviados (os campos ainda nao existem no formulario FXP-NA; nao inventar IDs).
 */
@ConfigurationProperties(prefix = "softexpert.workflow")
class WorkFlowEnvrioment(
    val idProcess : String,
    val tableName : String,
    val docCategory : String,
    val idAtividadeUpload : String,
    val campoSerie : String? = null,
    val campoChaveAcesso : String? = null) {

    init {
        val campos = listOfNotNull(campoSerie, campoChaveAcesso).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        require(campos.size == campos.toSet().size) {
            "softexpert.workflow.campoSerie e campoChaveAcesso nao podem ser o mesmo campo"
        }
        require(campos.none { it in CAMPOS_FIXOS }) {
            "softexpert.workflow.campoSerie/campoChaveAcesso nao podem repetir um campo ja enviado ($CAMPOS_FIXOS)"
        }
    }

    companion object {
        /** Campos que Duplicata.getSoftexpertMap ja envia. */
        val CAMPOS_FIXOS = setOf(
            "dataemissao", "dtinclusao", "razaotomador", "cpfcnpjtomador", "cnpjprestador", "razaoprestador",
            "numdocfiscal", "valorbruto", "valorliquido", "tipodoc",
        )
    }
}
