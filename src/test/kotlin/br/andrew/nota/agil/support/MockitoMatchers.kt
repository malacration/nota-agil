package br.andrew.nota.agil.support

import org.mockito.ArgumentMatchers

// Os matchers do Mockito retornam null; o tipo generico evita a verificacao Kotlin
// antes de a chamada ser interceptada pelo mock.
@Suppress("UNCHECKED_CAST")
fun <T> anyValue(): T = ArgumentMatchers.any<T>() ?: (null as T)
