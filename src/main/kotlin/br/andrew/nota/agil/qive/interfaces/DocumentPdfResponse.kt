package br.andrew.nota.agil.qive.interfaces

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Base64

@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentPdfResponse(
    val data: DocumentPdfData,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentPdfData(
    @JsonAlias("access_key", "ID")
    val id: String,
    @JsonProperty("encoded_pdf")
    val encodedPdf: String,
){

    fun decodePdf() : ByteArray{
        val normalized = encodedPdf
            .substringAfter(",", encodedPdf)
            .trim()
        return Base64.getMimeDecoder().decode(normalized)
    }
}
