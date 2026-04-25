package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import java.io.*
import java.net.*
import javax.net.ssl.*
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tlsSocket: SSLSocket? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("ProtoVPN", msg)
        try { logCallback?.invoke(msg) } catch(e: Exception) {}
    }
    
    override fun onCreate() { super.onCreate() }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ?? TRATAR STOP CORRETAMENTE
        if (intent?.action == "STOP") {
            log("?? STOP recebido!")
            stopVpn()
            return START_NOT_STICKY
        }
        
        if (!isRunning) { thread { startVpn() } }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            log("[1/4] TLS...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress("168.138.147.212", 443), 10000)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAll()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket(rawSocket, "168.138.147.212", 443, true) as SSLSocket
            tlsSocket?.startHandshake()
            
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            protect(tlsSocket!!)
            log("✅ TLS")
            
            log("[2/4] VPN...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("168.138.147.212", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN")
            
            log("[3/4] Encaminhamento...")
            val tlsIn = tlsSocket!!.inputStream
            val tlsOut = tlsSocket!!.outputStream
            
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) { len = vpnIn.read(buf); if (len > 0) { tlsOut.write(buf, 0, len); tlsOut.flush() } }
                } catch (e: Exception) {}
            }
            
            thread(name = "Download") {
                try {
                    val vpnOut = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) { len = tlsIn.read(buf); if (len > 0) { vpnOut.write(buf, 0, len); vpnOut.flush() } }
                } catch (e: Exception) {}
            }
            
            log("[4/4] ?? VPN COMPLETA!")
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        log("?? stopVpn()")
        isRunning = false
        
        // ?? Fechar sockets PRIMEIRO (isso libera a chave VPN!)
        try { tlsSocket?.close() } catch(e: Exception) { log("Erro ao fechar TLS: ${e.message}") }
        try { vpnInterface?.close() } catch(e: Exception) { log("Erro ao fechar VPN: ${e.message}") }
        
        tlsSocket = null
        vpnInterface = null
        
        // ?? Depois parar o serviço
        stopForeground(true)
        stopSelf()
        
        log("✅ VPN completamente parada")
    }
    
    override fun onDestroy() {
        log("onDestroy()")
        stopVpn()
        super.onDestroy()
    }
    
    override fun onRevoke() {
        // ?? Quando o sistema revoga a VPN
        log("⚠️ onRevoke() - VPN revogada pelo sistema!")
        stopVpn()
        super.onRevoke()
    }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
