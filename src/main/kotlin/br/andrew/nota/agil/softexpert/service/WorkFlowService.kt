package br.andrew.nota.agil.softexpert.service

import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.wsdl.document.AttribData
import br.andrew.wsdl.document.File
import br.andrew.wsdl.document.KeyWordData
import br.andrew.wsdl.document.MembersData
import br.andrew.wsdl.document.NewDocument2RequestType
import br.andrew.wsdl.document.NewDocument2ResponseType
import br.andrew.wsdl.workflow.*
import br.andrew.wsdl.workflow.WorkflowPortType
import org.apache.tika.Tika
import org.apache.tika.mime.MimeTypes
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date

@Service
class WorkFlowService(
    val wsdl : WorkflowPortType,
    val document : DocumentService
) {

    fun instanciaFluxo(idProcess : String,
                       title : String,
                       data : Map<String,Map<String,String>> = mapOf()) : NewWorkflowEditDataResponseType {

        val entrys = data.map { (k, v) ->
            EntityArray().also {
                it.entityID = k
                it.entityAttributeList = v.map { (k2, v2) ->
                    AttributeData().also {
                        it.entityAttributeID = k2
                        it.entityAttributeValue = v2
                    }
                }.toTypedArray()
            }
        }.toTypedArray()

        val newWorkFlowData = NewWorkflowEditDataRequestType().also {
            it.processID = idProcess;
            it.workflowTitle = title
            it.entityList = entrys
        }
        return wsdl.newWorkflowEditData(newWorkFlowData).also {
            if(it.status != "SUCCESS") throw Exception("Erro SE ${it.detail}")
        }
    }

    fun associa(activity : String, taskInstance: NewWorkflowEditDataResponseType, document: NewDocument2ResponseType): NewAssocDocumentResponseType {
        return associa(activity,taskInstance.recordID,document.recordID)
    }

    fun associa(activity : String, taskInstance: String, document: String): NewAssocDocumentResponseType {
        return wsdl.newAssocDocument(NewAssocDocumentRequestType().also {
            it.workflowID = taskInstance
            it.activityID = activity
            it.documentID = document
        }).also {
            if(it.status != "SUCCESS") throw Exception("Erro SE ${it.detail}")
        }
    }

    fun executaAtividade(workflowId : String, atividade : String, actionSequence : Int = 1): ExecuteActivityResponseType {
        return this.wsdl.executeActivity(ExecuteActivityRequestType(
            workflowId,atividade, BigInteger(actionSequence.toString()),null,null)
        )
    }
}
