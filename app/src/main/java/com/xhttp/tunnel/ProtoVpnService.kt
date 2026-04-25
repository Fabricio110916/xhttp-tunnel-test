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
            log("═══════════════════════════════════")
            log("?? VPN + WebSocket (SEM encaminhamento)")
            log("═══════════════════════════════════")
            
            // PASSO 1: TLS
            log("[1/4] TLS...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress("168.138.147.212", 443), 10000)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAll()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket(rawSocket, "oracle.koom.pp.ua", 443, true) as SSLSocket
            tlsSocket?.startHandshake()
            log("✅ TLS: ${tlsSocket?.session?.cipherSuite}")
            
            // PASSO 2: WebSocket
            log("[2/4] WebSocket...")
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("GET /ssh HTTP/1.1\r\n")
            w.write("Host: oracle.koom.pp.ua\r\n")
            w.write("Upgrade: websocket\r\n")
            w.write("Connection: Upgrade\r\n")
            w.write("\r\n")
            w.flush()
            
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                log("   $line")
                if (line!!.isEmpty()) break
            }
            log("✅ WebSocket OK")
            
            // PASSO 3: PROTEGER o socket (NÃO passa pela VPN!)
            protect(tlsSocket!!)
            log("[3/4] Socket protegido")
            
            // PASSO 4: VPN (SEM encaminhamento manual!)
            log("[4/4] VPN...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN CRIADA!")
            
            log("")
            log("═══════════════════════════════════")
            log("?? VPN ATIVA!")
            log("═══════════════════════════════════")
            log("?? IP: 10.8.0.2")
            log("?? TLS: ${tlsSocket?.session?.cipherSuite}")
            log("?? WebSocket: Conectado")
            log("??️ Socket: Protegido (não passa pela VPN)")
            log("?? Ícone de VPN: DEVE aparecer!")
            log("")
            log("?? O Android vai rotear o tráfego automaticamente!")
            log("⏸ Monitorando...")
            
            // Monitorar
            var seg = 0
            while (isRunning) {
                Thread.sleep(5000)
                seg += 5
                val tlsStatus = try { tlsSocket?.isConnected == true } catch(e: Exception) { false }
                log("⏰ ${seg}s | VPN: ATIVA | TLS: ${if (tlsStatus) "CONECTADO" else "DESCONECTADO"}")
            }
            
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
