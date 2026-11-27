package br.andrew.nota.agil.softexpert.infrastructure.interfaces

/**
 * Instancia de workflow retornada por instance.php, filtrada pelo usuario iniciador.
 * Nao representa uma tarefa atribuida ao usuario.
 *
 * Exemplo: { "oid":"147949","identifier":"FXP-N083393","title":"...",
 *            "processStatus":"Andamento","deadline":"Em dia","slaStatus":"Nao aplicavel",
 *            "instanceStatus":"sitbanco - Retorno Banco","automationProductCode":39,
 *            "fgStatus":1,"fgSlaStatus":null,"fgDeadline":1 }
 */
class WorkflowInstance(
    val oid: String? = null,
    val identifier: String? = null,
    val title: String? = null,
    val processStatus: String? = null,
    val deadline: String? = null,
    val slaStatus: String? = null,
    val instanceStatus: String? = null,
    val automationProductCode: Int? = null,
    val fgStatus: Int? = null,
    val fgSlaStatus: Int? = null,
    val fgDeadline: Int? = null,
)
