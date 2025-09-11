package br.andrew.nota.agil.qive.interfaces

import br.andrew.nota.agil.qive.interfaces.Status

class ReceivedResponse<T>(
    val data: List<T> = emptyList(),
    val page : Page?,
    val count : Int?,
    val signatura : String?,
    val status : Status
){

}



