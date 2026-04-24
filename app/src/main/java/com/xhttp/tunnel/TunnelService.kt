package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.*
import javax.net.ssl.*

class TunnelService : Service() {
    
    private var tlsSocket: SSLSocket? = null
    private var proxyServer: ServerSocket? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "xhttp_tunnel"
        private const val PROXY_PORT = 1080
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("Tunnel", msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification("Túnel ativo"))
            Thread { startTunnel() }.start()
        }
        return START_STICKY
    }
    
    private fun startTunnel() {
        isRunning = true
        
        try {
            log("[1/3] Conectando XHTTP...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket("168.138.147.212", 443) as SSLSocket
            tlsSocket?.startHandshake()
            
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ XHTTP OK")
            
            log("[2/3] Iniciando SOCKS5 local...")
            proxyServer = ServerSocket(PROXY_PORT)
            log("✅ SOCKS5 em 127.0.0.1:$PROXY_PORT")
            
            updateNotification("SOCKS5 Proxy", "Porta $PROXY_PORT")
            
            log("[3/3] ?? Proxy ativo!")
            log("?? Configure: 127.0.0.1:$PROXY_PORT")
            log("   Tipo: SOCKS5")
            
            // Aceitar conexões
            while (isRunning) {
                val client = proxyServer?.accept() ?: break
                Thread { handleClient(client) }.start()
            }
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopSelf()
        }
    }
    
    private fun handleClient(client: Socket) {
        try {
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()
            val tlsIn = tlsSocket!!.inputStream
            val tlsOut = tlsSocket!!.outputStream
            
            // Handshake SOCKS5 simples (sem autenticação)
            clientIn.skip(3)
            clientOut.write(byteArrayOf(5, 0))
            
            // Encaminhar
            Thread { try { clientIn.copyTo(tlsOut) } catch(e: Exception) {} }.start()
            Thread { try { tlsIn.copyTo(clientOut) } catch(e: Exception) {} }.start()
            
        } catch (e: Exception) {
            try { client.close() } catch(e: Exception) {}
        }
    }
    
    override fun onDestroy() {
        isRunning = false
        try { tlsSocket?.close() } catch(e: Exception) {}
        try { proxyServer?.close() } catch(e: Exception) {}
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "XHTTP Tunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("XHTTP Proxy").setContentText(text)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
    
    private fun updateNotification(title: String, content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        )
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
