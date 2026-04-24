package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.concurrent.thread
import libDTProto.*

class ProtoVpnService : VpnService() {
    
    private var startThread: Thread? = null
    private var stopThread: Thread? = null
    private var client: DTProtoClient? = null
    
    companion object {
        private const val TAG = "ProtoVpnService"
        private const val CHANNEL_ID = "dtproto_vpn"
        private const val NOTIFICATION_ID = 1001
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i(TAG, msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
        
        // Obter parâmetros
        val host = intent?.getStringExtra("host") ?: "oracle.koom.pp.ua"
        val port = intent?.getStringExtra("port") ?: "443"
        val sni = intent?.getStringExtra("sni") ?: host
        val proxy = intent?.getStringExtra("proxy") ?: host
        val tls = intent?.getBooleanExtra("tls", true) ?: true
        val username = intent?.getStringExtra("username") ?: ""
        val password = intent?.getStringExtra("password") ?: ""
        
        log("?? DTProto XHTTP VPN")
        log("?? $host:$port")
        log("?? TLS: $tls | SNI: $sni")
        
        startThread?.interrupt()
        startThread = thread(name = "dtproto-start") {
            try {
                val cfg = DTProtoClientConfig()
                cfg.setXHTTPHost(host)
                cfg.setPort(port)
                cfg.setXHTTPTLS(tls)
                cfg.setXHTTPServerName(sni)
                cfg.setXHTTPInsecure(true)
                cfg.setXHTTPUploadBufferSize(32768L)
                cfg.setUsername(username)
                cfg.setPassword(password)
                cfg.setKeepAliveInterval(120L)
                cfg.setKeepAliveMaxRetry(5L)
                cfg.setReconnectDelay(3L)
                cfg.setTimeout(30L)
                
                val tunBuilder = TunBuilder { ip ->
                    val builder = Builder()
                        .setSession("DTProto")
                        .setMtu(1500)
                        .addAddress(ip ?: "10.8.0.2", 32)
                        .addDnsServer("1.1.1.1")
                        .addRoute("0.0.0.0", 0)
                    val fd = builder.establish()
                        ?: throw IllegalStateException("Falha ao criar TUN")
                    fd.detachFd().toLong()
                }
                
                val socketOpener = SocketOpener {
                    val socket = java.net.Socket()
                    ParcelFileDescriptor.fromSocket(socket).detachFd().toLong()
                }
                
                val socketProtector = SocketProtector { fd ->
                    val socket = java.net.Socket()
                    protect(socket)
                }
                
                val statusListener = StatusListener { status, error ->
                    log("?? $status")
                    updateNotification(status ?: "")
                    if (status == "CONNECTED") log("✅ VPN CONECTADA!")
                    if (status == "ERROR" && error != null) log("❌ ${error.message}")
                }
                
                val logHandler = LogHandler { level, _, message ->
                    val lvl = if (level.toInt() == 0) Log.DEBUG else Log.INFO
                    Log.println(lvl, TAG, message ?: "")
                }
                
                client = LibDTProto.new_(cfg, tunBuilder, socketOpener, socketProtector, statusListener, logHandler)
                client?.start()
                
            } catch (e: Exception) {
                log("❌ Erro: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        stopThread?.interrupt()
        stopThread = thread(name = "dtproto-stop") {
            try { client?.stop() } catch (e: Exception) {}
        }
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DTProto XHTTP", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("DTProto XHTTP")
        .setContentText(content)
        .setOngoing(true)
        .build()
    
    private fun updateNotification(status: String) {
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification("Status: $status")
        )
    }
}
