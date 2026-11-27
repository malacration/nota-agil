package br.andrew.nota.agil.softexpert.infrastructure.interfaces

/**
 * Envelope generico de resposta do SoftExpert REST.
 *
 * Sucesso: { "success": true, "response": <T> }
 * Erro:    { "success": false, "message": "...", "status": 500 }
 */
class SoftExpertResponse<T>(
    val success: Boolean = false,
    val response: T? = null,
    val message: String? = null,
    val status: Int? = null,
) {

    /** Retorna o payload ou lança quando o SoftExpert sinalizou falha. */
    fun getOrThrow(): T {
        if (!success || response == null)
            throw SoftExpertException(status, message ?: "resposta sem sucesso do SoftExpert")
        return response
    }
}

/** Bloco de resultados do SoftExpert. O total pode refletir qtLimit, nao uma contagem global. */
class SoftExpertResults<T>(
    val total: Int = 0,
    val results: List<T> = emptyList(),
)

class SoftExpertException(val status: Int?, message: String) :
    RuntimeException("SoftExpert erro (status=$status): $message")
