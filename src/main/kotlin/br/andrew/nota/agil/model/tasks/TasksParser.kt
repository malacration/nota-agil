package br.andrew.nota.agil.model.tasks

class TasksParser(val task: Task) {

    fun toTaskUploadDocument(): Task {
        if(task.duplicata!!.chaveAcesso == null)
            throw Exception("Erro ao criar task, sem chave de acesso")

        if(task.recordID == null)
            throw Exception("Nao é permitido taskUpload sem recordID preenchido")
        if(task.recordKey == null)
            throw Exception("Nao é permitido taskUpload sem recordKey preenchido")

        val chaveAcesso = task.duplicata.chaveAcesso

        return Task(chaveAcesso+"-"+TaskTypes.UploadPdf,task.duplicata.getTitulo(),TaskStatus.READY,TaskTypes.UploadPdf,task.duplicata).also {
            it.recordID = task.recordID
            it.recordKey = task.recordKey
        }
    }

    /**
     * Anexo de nota importada de ERP. Nota sem id do Qive (NFS-e do SAP, notas do Dealer) tem a
     * [Duplicata.chaveAcesso] nula, entao o id usa a task de criacao, que ja e unica.
     */
    fun toTaskUploadErp(): Task {
        val notaErp = task.notaErp ?: throw Exception("Task de ERP sem referencia da nota")
        if(task.recordID == null)
            throw Exception("Nao é permitido taskUpload sem recordID preenchido")
        if(task.recordKey == null)
            throw Exception("Nao é permitido taskUpload sem recordKey preenchido")

        val base = task.id.removeSuffix("-" + TaskTypes.CreateTask)
        return Task(base + "-" + TaskTypes.UploadPdfErp, task.name, TaskStatus.READY, TaskTypes.UploadPdfErp, task.duplicata).also {
            it.recordID = task.recordID
            it.recordKey = task.recordKey
            it.origem = task.origem
            it.notaErp = notaErp
            it.tomador = task.tomador
        }
    }
}
