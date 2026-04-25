package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import java.io.*
import java.net.*
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
            log("?? VPN + WebSocket")
            log("?? oracle.koom.pp.ua:80")
            log("═══════════════════════════════════")
            
            // PASSO 1: TCP na porta 80
            log("[1/3] Conectando TCP...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress("168.138.147.212", 80), 10000)
            socket = rawSocket
            log("✅ TCP conectado!")
            
            // PASSO 2: WebSocket handshake
            log("[2/3] WebSocket...")
            val w = OutputStreamWriter(socket!!.outputStream)
            w.write("GET / HTTP/1.1\r\n")
            w.write("Host: oracle.koom.pp.ua\r\n")
            w.write("Upgrade: websocket\r\n")
            w.write("Connection: Upgrade\r\n")
            w.write("\r\n")
            w.flush()
            
            val r = BufferedReader(InputStreamReader(socket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                log("   $line")
                if (line!!.isEmpty()) break
            }
            log("✅ WebSocket 101 OK")
            
            // PASSO 3: VPN (SEM encaminhamento!)
            log("[3/3] Criando VPN...")
            val builder = Builder()
                .setSession("XHTTP VPN")
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
            log("?? Porta: 80")
            log("?? WebSocket: oracle.koom.pp.ua")
            log("?? Ícone VPN: DEVE aparecer!")
            log("")
            log("?? O Android roteia automaticamente!")
            log("⏸ Monitorando...")
            
            // Monitorar
            var seg = 0
            while (isRunning) {
                Thread.sleep(5000)
                seg += 5
                val wsStatus = try { socket?.isConnected == true } catch(e: Exception) { false }
                log("⏰ ${seg}s | VPN: ATIVA | WS: ${if (wsStatus) "OK" else "OFF"}")
            }
            
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
        log("⏹ VPN parada")
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
