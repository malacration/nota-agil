package br.andrew.nota.agil.softexpert.infrastructure.interfaces

data class SoftExpertUser(
    val matricula: String,
    val login: String?,
    val nome: String?,
    val email: String?,
    val telefone: String?,
    val areas: List<SoftExpertUserVinculo>,
    val funcoes: List<SoftExpertUserVinculo>,
    val equipes: List<SoftExpertUserVinculo>,
)

data class SoftExpertUserVinculo(val id: String?, val nome: String?)

data class WorkflowDetails(
    val workflowId: String,
    val title: String?,
    val processId: String?,
    val instanceStatus: String?,
    val workflowStatus: String?,
    /** Matricula, nao ADUSER.CDUSER e nem login. */
    val starterUser: String?,
    val starterExternalUser: String?,
    val startDate: String?,
)
