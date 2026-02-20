package br.andrew.nota.agil.softexpert.service

import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.wsdl.document.AttribData
import br.andrew.wsdl.document.DocumentoPortType
import br.andrew.wsdl.document.File
import br.andrew.wsdl.document.KeyWordData
import br.andrew.wsdl.document.MembersData
import br.andrew.wsdl.document.NewDocument2RequestType
import br.andrew.wsdl.document.NewDocument2ResponseType
import org.apache.tika.Tika
import org.apache.tika.mime.MimeTypes
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.util.Date

@Service
class DocumentService(
    val wsdl : DocumentoPortType
) {


    fun save(
        pdf : DocumentPdfResponse,
        title: String,
        category: String,
        date : Date = Date()): NewDocument2ResponseType? {
        val bytesPdf = pdf.data.decodePdf()
        val ext = MimeTypes.getDefaultMimeTypes().forName(Tika().detect(bytesPdf)).extension
        return wsdl.newDocument2(NewDocument2RequestType(category,
            "",
            title,
            "",
            SimpleDateFormat("yyyy-MM-dd").format(date),
            arrayOf<AttribData>(),"",
            arrayOf<MembersData>(),
            "",
            arrayOf<File>(File("${title}${ext}",bytesPdf,"")),
            arrayOf<KeyWordData>(),
            "",
            ""
        )).also {
            if(it.status == "FAILURE")
                throw Exception("Falha ao criar documento. Causa: "+it.detail,)
        }
    }
}