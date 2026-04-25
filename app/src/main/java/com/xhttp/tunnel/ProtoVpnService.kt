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
    private var socket: Socket? = null
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
            log("═══════════════════════════════════")
            log("?? XHTTP WebSocket VPN")
            log("═══════════════════════════════════")
            log("?? oracle.koom.pp.ua:80")
            log("?? Payload: GET / HTTP/1.1")
            log("?? Upgrade: websocket")
            log("")
            
            // PASSO 1: TCP
            log("[1/5] TCP...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress("168.138.147.212", 80), 10000)
            socket = rawSocket
            log("✅ TCP conectado na porta 80")
            
            // PASSO 2: WebSocket Handshake
            log("[2/5] WebSocket...")
            val w = OutputStreamWriter(socket!!.outputStream)
            w.write("GET / HTTP/1.1\r\n")
            w.write("Host: oracle.koom.pp.ua\r\n")
            w.write("Upgrade: websocket\r\n")
            w.write("Connection: Upgrade\r\n")
            w.write("\r\n")
            w.flush()
            log("   ?? Payload enviado")
            
            // Ler resposta
            val r = BufferedReader(InputStreamReader(socket!!.inputStream))
            var line: String?
            var status = ""
            while (r.readLine().also { line = it } != null) {
                log("   ?? $line")
                if (line!!.startsWith("HTTP/")) status = line!!
                if (line!!.isEmpty()) break
            }
            
            if (status.contains("101")) {
                log("✅ WebSocket conectado! (101)")
            } else {
                log("❌ Servidor respondeu: $status")
                throw Exception("WebSocket falhou: $status")
            }
            
            // PASSO 3: Proteger socket
            protect(socket!!)
            log("[3/5] Socket protegido")
            
            // PASSO 4: VPN
            log("[4/5] VPN...")
            val builder = Builder()
                .setSession("XHTTP WS VPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN criada!")
            
            // PASSO 5: Encaminhamento
            log("[5/5] Encaminhando...")
            
            val sockIn = socket!!.inputStream
            val sockOut = socket!!.outputStream
            val fd = vpnInterface!!.fileDescriptor
            
            // Upload: VPN -> Socket
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(fd)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = vpnIn.read(buf)
                        if (len > 0) { sockOut.write(buf, 0, len); sockOut.flush() }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? ${e.message}")
                }
            }
            
            // Download: Socket -> VPN
            thread(name = "Download") {
                try {
                    val vpnOut = ParcelFileDescriptor.AutoCloseOutputStream(vpnInterface!!)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = sockIn.read(buf)
                        if (len > 0) { vpnOut.write(buf, 0, len); vpnOut.flush() }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? ${e.message}")
                }
            }
            
            log("")
            log("═══════════════════════════════════")
            log("?? VPN COMPLETA!")
            log("═══════════════════════════════════")
            log("?? IP: 10.8.0.2")
            log("?? Porta: 80 (HTTP)")
            log("?? WebSocket: oracle.koom.pp.ua")
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        try { socket?.close() } catch(e: Exception) {}
        try { vpnInterface?.close() } catch(e: Exception) {}
        socket = null; vpnInterface = null
        stopForeground(true); stopSelf()
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
