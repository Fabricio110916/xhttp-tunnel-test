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
        if (intent?.action == "STOP") { stopVpn(); return START_NOT_STICKY }
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
            
            // Upload: FileInputStream FUNCIONA
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = vpnIn.read(buf)
                        if (len > 0) { tlsOut.write(buf, 0, len); tlsOut.flush() }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? ${e.message}")
                }
            }
            
            // Download: AutoCloseOutputStream FUNCIONA no Android 14
            thread(name = "Download") {
                try {
                    val vpnOut = ParcelFileDescriptor.AutoCloseOutputStream(vpnInterface!!)
                    val buf = ByteArray(32768)
                    var len: Int
                    var total = 0L
                    while (isRunning) {
                        len = tlsIn.read(buf)
                        if (len > 0) {
                            vpnOut.write(buf, 0, len)
                            vpnOut.flush()
                            total += len
                            if (total % 50000 == 0L) log("?? Download: $total bytes")
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? ${e.message}")
                }
            }
            
            log("[4/4] ?? VPN COMPLETA!")
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        try { tlsSocket?.close() } catch(e: Exception) {}
        try { vpnInterface?.close() } catch(e: Exception) {}
        tlsSocket = null; vpnInterface = null
        stopForeground(true); stopSelf()
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
