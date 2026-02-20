package br.andrew.nota.agil.infrastructure

import org.apache.axis.components.net.BooleanHolder
import org.apache.axis.components.net.SecureSocketFactory
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Hashtable
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class InsecureAxisSocketFactory(
    attributes: Hashtable<*, *>,
) : SecureSocketFactory {

    @Suppress("unused")
    private val ignoredAttributes = attributes

    private val sslSocketFactory by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        })

        SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }.socketFactory
    }

    override fun create(
        host: String,
        port: Int,
        otherHeaders: StringBuffer?,
        useFullURL: BooleanHolder?,
    ): Socket {
        val resolvedPort = if (port in 1..65535) port else 443
        return sslSocketFactory.createSocket(host, resolvedPort)
    }
}
