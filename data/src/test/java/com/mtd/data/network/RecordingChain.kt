package com.mtd.data.network

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Minimal [Interceptor.Chain] for unit-testing interceptors without a real network — captures
 * the [Request] that the interceptor forwards via [proceed].
 */
class RecordingChain(private val request: Request,
                     override val followSslRedirects: Boolean,
                     override val followRedirects: Boolean,
                     override val dns: Dns,
                     override val socketFactory: SocketFactory,
                     override val retryOnConnectionFailure: Boolean,
                     override val authenticator: Authenticator,
                     override val cookieJar: CookieJar,
                     override val cache: Cache?,
                     override val proxy: Proxy?,
                     override val proxySelector: ProxySelector,
                     override val proxyAuthenticator: Authenticator,
                     override val sslSocketFactoryOrNull: SSLSocketFactory?,
                     override val x509TrustManagerOrNull: X509TrustManager?,
                     override val hostnameVerifier: HostnameVerifier,
                     override val certificatePinner: CertificatePinner,
                     override val connectionPool: ConnectionPool,
                     override val eventListener: EventListener
) : Interceptor.Chain {

    lateinit var proceeded: Request
        private set

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        proceeded = request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody(null))
            .build()
    }

    override fun connection(): Connection? = null
    override fun call(): Call = throw UnsupportedOperationException("not used")
    override fun connectTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun readTimeoutMillis(): Int = 0
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun writeTimeoutMillis(): Int = 0
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withDns(dns: Dns): Interceptor.Chain =this

    override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain =this
    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain =this

    override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain =this
    override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain =this

    override fun withCache(cache: Cache?): Interceptor.Chain =this
    override fun withProxy(proxy: Proxy?): Interceptor.Chain =this
    override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain =this

    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain =this

    override fun withSslSocketFactory(
        sslSocketFactory: SSLSocketFactory?,
        x509TrustManager: X509TrustManager?
    ): Interceptor.Chain =this

    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain =this
    override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain =this

    override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain =this
}
