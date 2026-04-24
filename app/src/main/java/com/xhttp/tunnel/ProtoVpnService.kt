package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var startThread: Thread? = null
    private var stopThread: Thread? = null
    private var client: libDTProto.DTProtoClient? = null
    
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
        log("?? DTProto XHTTP VPN")
        log("?? oracle.koom.pp.ua:443")
        log("?? XHTTP TLS: SIM")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
        
        startThread?.interrupt()
        startThread = thread(name = "dtproto-start") {
            try {
                log("⏳ Configurando DTProto...")
                
                // Configuração com CAMPOS REAIS do AAR
                val cfg = libDTProto.DTProtoClientConfig().apply {
                    // XHTTP
                    setXHTTPHost("oracle.koom.pp.ua")       // Host de conexão
                    setPort("443")                            // Porta
                    setXHTTPTLS(true)                         // TLS ativado
                    setXHTTPServerName("oracle.koom.pp.ua")  // SNI
                    setXHTTPInsecure(true)                    // Ignorar cert inválido
                    setXHTTPUploadBufferSize(32768L)          // Buffer 32KB
                    
                    // Autenticação (vazia = sem auth)
                    setUsername("")
                    setPassword("")
                    
                    // Timeouts
                    setKeepAliveInterval(120L)
                    setKeepAliveMaxRetry(5L)
                    setReconnectDelay(3L)
                    setTimeout(30L)
                }
                
                log("⚙️ XHTTP Host: ${cfg.getXHTTPHost()}")
                log("   Porta: ${cfg.getPort()} | TLS: ${cfg.getXHTTPTLS()}")
                log("   SNI: ${cfg.getXHTTPServerName()}")
                
                // TunBuilder
                val tunBuilder = libDTProto.TunBuilder { ip ->
                    val builder = Builder()
                        .setSession("DTProto")
                        .setMtu(1500)
                        .addAddress(ip ?: "10.8.0.2", 32)
                        .addDnsServer("1.1.1.1")
                        .addDnsServer("8.8.8.8")
                        .addRoute("0.0.0.0", 0)
                    val fd = builder.establish()
                        ?: throw IllegalStateException("Falha ao criar TUN")
                    fd.detachFd().toLong()
                }
                
                // SocketOpener
                val socketOpener = libDTProto.SocketOpener {
                    val socket = java.net.Socket()
                    ParcelFileDescriptor.fromSocket(socket).detachFd().toLong()
                }
                
                // SocketProtector
                val socketProtector = libDTProto.SocketProtector { fd ->
                    // Proteger o socket
                    protect(java.net.Socket())
                }
                
                // Status Listener
                val statusListener = libDTProto.StatusListener { status, error ->
                    log("?? $status")
                    updateNotification(status ?: "desconhecido")
                    
                    if (status == "CONNECTED") log("✅ VPN CONECTADA!")
                    if (status == "ERROR" && error != null) {
                        log("❌ ${error.message}")
                    }
                }
                
                // Log Handler
                val logHandler = libDTProto.LogHandler { level, _, message ->
                    val androidLevel = when (level.toInt()) {
                        0 -> Log.DEBUG; 1 -> Log.INFO; 2 -> Log.WARN
                        3 -> Log.ERROR; else -> Log.ASSERT
                    }
                    Log.println(androidLevel, TAG, message ?: "")
                }
                
                // Criar cliente com OS 5 PARÂMETROS CORRETOS!
                client = libDTProto.LibDTProto.new_(
                    cfg,
                    tunBuilder,
                    socketOpener,
                    socketProtector,
                    statusListener,
                    logHandler
                )
                
                log("▶ Iniciando DTProto Client...")
                client?.start()
                
            } catch (e: Exception) {
                log("❌ Erro fatal: ${e.message}")
                e.printStackTrace()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        log("?? Encerrando...")
        stopThread?.interrupt()
        stopThread = thread(name = "dtproto-stop") {
            try { client?.stop() }
            catch (e: Exception) { log("Erro ao parar: ${e.message}") }
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
        .setContentTitle("DTProto XHTTP VPN")
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
