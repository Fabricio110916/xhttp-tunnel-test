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
        logCallback?.invoke(msg)
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
            // PASSO 1: TLS (já funciona)
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
            
            // PASSO 2: VPN (já funciona)
            log("[2/4] VPN...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("168.138.147.212", 32)
                .addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN")
            
            // PASSO 3: Encaminhamento (COM PROTEÇÃO em cada thread!)
            log("[3/4] Encaminhamento...")
            
            val tlsIn = tlsSocket!!.inputStream
            val tlsOut = tlsSocket!!.outputStream
            
            // Upload thread
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
                    // Apenas log, NÃO crasha!
                    if (isRunning) Log.d("ProtoVPN", "Upload: ${e.message}")
                }
            }
            
            // Download thread
            thread(name = "Download") {
                try {
                    val vpnOut = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = tlsIn.read(buf)
                        if (len > 0) { vpnOut.write(buf, 0, len); vpnOut.flush() }
                    }
                } catch (e: Exception) {
                    // Apenas log, NÃO crasha!
                    if (isRunning) Log.d("ProtoVPN", "Download: ${e.message}")
                }
            }
            
            log("[4/4] ?? VPN COMPLETA!")
            log("?? IP: 10.8.0.2")
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        try { tlsSocket?.close() } catch(e: Exception) {}
        try { vpnInterface?.close() } catch(e: Exception) {}
        stopSelf()
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
