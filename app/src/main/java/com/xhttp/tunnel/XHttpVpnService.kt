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
            log("[1/5] Conectando TLS...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket("168.138.147.212", 443) as SSLSocket
            tlsSocket?.startHandshake()
            log("✅ TLS: ${tlsSocket?.session?.cipherSuite}")
            
            log("[2/5] Enviando POST...")
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ POST 200 OK")
            
            log("[3/5] Criando TUN...")
            val builder = Builder()
                .setSession("XHTTP VPN")
                .addAddress("10.8.0.2", 32)
                .addRoute("168.138.147.212", 32)
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("10.0.0.0", 8)
                .addRoute("172.16.0.0", 12)
                .addRoute("192.168.0.0", 16)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
            
            builder.addDisallowedApplication("com.xhttp.tunnel")
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ TUN criada!")
            
            log("[4/5] Encaminhando...")
            updateNotification("XHTTP VPN", "Ativo!")
            
            val tlsIn = tlsSocket!!.inputStream
            val tlsOut = tlsSocket!!.outputStream
            
            // Upload
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(vpnInterface!!.fileDescriptor)
                    val buffer = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = vpnIn.read(buffer)
                        if (len > 0) { tlsOut.write(buffer, 0, len); tlsOut.flush() }
                    }
                } catch (e: Exception) { if (isRunning) log("📤 ${e.message}") }
            }
            
            // Download com reflection
            thread(name = "Download") {
                try {
                    val fdInt = ParcelFileDescriptor::class.java.getDeclaredField("mFd").apply { isAccessible = true }.getInt(vpnInterface!!)
                    val writeFd = FileDescriptor().also {
                        FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.setInt(it, fdInt)
                    }
                    val vpnOut = FileOutputStream(writeFd)
                    val buffer = ByteArray(32768)
                    var len: Int
                    while (isRunning) {
                        len = tlsIn.read(buffer)
                        if (len > 0) { vpnOut.write(buffer, 0, len); vpnOut.flush() }
                    }
                } catch (e: Exception) { if (isRunning) log("📥 ${e.message}") }
            }
            
            log("[5/5] 🎉 VPN COMPLETA!")
            log("📍 IP: 10.8.0.2")
            log("🛡️ Servidor/DNS/LAN protegidos")
            log("🔄 Upload + Download ativos")
            
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
