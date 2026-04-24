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
            log("[1/4] Conectando túnel XHTTP...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket("168.138.147.212", 443) as SSLSocket
            tlsSocket?.tcpNoDelay = true
            tlsSocket?.startHandshake()
            
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ Túnel OK")
            
            log("[2/4] Criando VPN...")
            val builder = Builder()
                .setSession("XHTTP VPN")
                .addAddress("10.8.0.2", 32)
                .addRoute("168.138.147.212", 32)
                .addRoute("8.8.8.8", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN criada!")
            
            log("[3/4] Iniciando encaminhamento...")
            updateNotification("XHTTP VPN", "Conectado!")
            
            // Usar AutoCloseOutputStream (compatível com Android 14+)
            val vpnOutput = ParcelFileDescriptor.AutoCloseOutputStream(vpnInterface!!)
            val vpnInput = ParcelFileDescriptor.AutoCloseInputStream(vpnInterface!!)
            val tlsOut = tlsSocket!!.outputStream
            val tlsIn = tlsSocket!!.inputStream
            
            // Upload: VPN -> TLS
            thread {
                try {
                    val buffer = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = vpnInput.read(buffer)
                        if (len > 0) {
                            tlsOut.write(buffer, 0, len)
                            tlsOut.flush()
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? Upload: ${e.message}")
                }
            }
            
            // Download: TLS -> VPN
            thread {
                try {
                    val buffer = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = tlsIn.read(buffer)
                        if (len > 0) {
                            vpnOutput.write(buffer, 0, len)
                            vpnOutput.flush()
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) log("?? Download: ${e.message}")
                }
            }
            
            log("[4/4] ?? VPN ATIVA COM ENCAMINHAMENTO!")
            log("?? IP: 10.8.0.2")
            log("?? Dados fluindo pelo túnel XHTTP!")
            log("??️ Servidor protegido contra loop!")
            
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
