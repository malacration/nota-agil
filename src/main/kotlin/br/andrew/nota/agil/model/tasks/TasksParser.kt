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
}