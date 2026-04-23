package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.*
import javax.net.ssl.*
import kotlin.concurrent.thread

class XHttpVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tlsSocket: SSLSocket? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "xhttp_vpn"
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("XHttpVPN", msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopVpn(); return START_NOT_STICKY }
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification("VPN Iniciando..."))
            thread { startVpn() }
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            log("[1/4] TLS...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket("168.138.147.212", 443) as SSLSocket
            tlsSocket?.startHandshake()
            log("✅ TLS")
            
            log("[2/4] POST...")
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ POST")
            
            log("[3/4] VPN (SEM encaminhamento)...")
            val b = Builder().setSession("XHTTP VPN").addAddress("10.8.0.2", 32).addRoute("0.0.0.0", 0).addDnsServer("8.8.8.8").setMtu(1500)
            vpnInterface = b.establish() ?: throw Exception("VPN null")
            log("✅ VPN criada!")
            
            log("[4/4] ?? VPN ATIVA! (sem encaminhamento)")
            log("?? IP: 10.8.0.2")
            log("⏸ Aguardando 30s sem encaminhar...")
            
            updateNotification("XHTTP VPN", "Ativa (sem tráfego)")
            
            // FICAR VIVO por 30 segundos SEM encaminhar
            for (i in 1..30) {
                if (!isRunning) break
                Thread.sleep(1000)
                if (i % 10 == 0) log("   ⏰ ${i}s - VPN ainda viva!")
            }
            
            if (isRunning) {
                log("")
                log("════════════════════════════════")
                log("✅ TESTE APROVADO!")
                log("VPN ficou estável por 30s!")
                log("SEM CRASH!")
                log("════════════════════════════════")
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
        stopForeground(true)
        stopSelf()
        log("⏹ Parado")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "XHTTP VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("XHTTP VPN").setContentText(text)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
    
    private fun updateNotification(title: String, content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        )
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    
    class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
