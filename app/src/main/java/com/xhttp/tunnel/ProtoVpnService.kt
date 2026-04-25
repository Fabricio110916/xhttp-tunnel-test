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
        
        init {
            try {
                System.loadLibrary("tun2socks")
                Log.i("ProtoVPN", "✅ libtun2socks.so carregada!")
            } catch (e: Exception) {
                Log.e("ProtoVPN", "❌ Erro libtun2socks: ${e.message}")
            }
        }
    }
    
    private external fun StartTun2socks(proxy: String, device: String, mtu: Int): Int
    private external fun StopTun2socks()
    
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
            log("?? VPN + tun2socks")
            
            // PASSO 1: WebSocket
            log("[1/3] WebSocket...")
            val rawSocket = Socket()
            protect(rawSocket)
            rawSocket.connect(InetSocketAddress("168.138.147.212", 80), 10000)
            socket = rawSocket
            
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
                if (line!!.isEmpty()) break
            }
            log("✅ WebSocket OK")
            
            // PASSO 2: VPN
            log("[2/3] VPN...")
            val builder = Builder()
                .setSession("XHTTP VPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
            
            builder.addDisallowedApplication("com.xhttp.tunnel")
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            val fd = vpnInterface!!.fileDescriptor
            log("✅ VPN criada!")
            
            // PASSO 3: SOCKS5 local (SEM tun2socks!)
            log("[3/3] SOCKS5...")
            thread(name = "SocksServer") {
                try {
                    val server = ServerSocket()
                    server.bind(InetSocketAddress("127.0.0.1", 1080))
                    protect(server)
                    log("?? SOCKS5 em 127.0.0.1:1080")
                    
                    while (isRunning) {
                        try {
                            val client = server.accept()
                            thread {
                                try {
                                    val sockIn = socket!!.inputStream
                                    val sockOut = socket!!.outputStream
                                    val clientIn = client.inputStream
                                    val clientOut = client.outputStream
                                    
                                    clientIn.skip(3)
                                    clientOut.write(byteArrayOf(5, 0))
                                    
                                    thread { try { clientIn.copyTo(sockOut) } catch(e: Exception) {} }
                                    thread { try { sockIn.copyTo(clientOut) } catch(e: Exception) {} }
                                } catch(e: Exception) {}
                            }
                        } catch (e: Exception) {
                            if (isRunning) log("SOCKS5: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("SOCKS5: ${e.message}")
                }
            }
            
            log("?? VPN COMPLETA!")
            log("?? IP: 10.8.0.2")
            log("??️ App excluído + sockets protegidos!")
            
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
}
