package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.*
import androidx.core.app.NotificationManagerCompat
import javax.net.ssl.*
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tlsSocket: SSLSocket? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "proto_vpn"
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("ProtoVPN", msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopVpn(); return START_NOT_STICKY }
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
            thread { startVpn() }
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            log("[1/3] Conectando TLS...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress("168.138.147.212", 443), 10000)
            
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAll()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket(rawSocket, "168.138.147.212", 443, true) as SSLSocket
            tlsSocket?.startHandshake()
            
            protect(tlsSocket!!)
            
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ Túnel XHTTP OK")
            
            log("[2/3] Criando VPN...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("168.138.147.212", 32)
                .addRoute("0.0.0.0", 0)
            
            vpnInterface = builder.establish() ?: throw Exception("VPN null")
            log("✅ VPN criada")
            
            log("[3/3] ?? VPN ATIVA!")
            log("?? IP: 10.8.0.2")
            
            updateNotification("Conectado!")
            
            val tlsIn = tlsSocket!!.inputStream
            val tlsOut = tlsSocket!!.outputStream
            
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) { len = vpnIn.read(buf); if (len > 0) { tlsOut.write(buf, 0, len); tlsOut.flush() } }
                } catch (e: Exception) {}
            }
            
            thread(name = "Download") {
                try {
                    val vpnOut = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    while (isRunning) { len = tlsIn.read(buf); if (len > 0) { vpnOut.write(buf, 0, len); vpnOut.flush() } }
                } catch (e: Exception) {}
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
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ProtoVPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("ProtoVPN")
        .setContentText(content)
        .setOngoing(true)
        .build()
    
    private fun updateNotification(status: String) {
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification("Status: $status")
        )
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
