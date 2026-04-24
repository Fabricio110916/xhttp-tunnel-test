package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.net.Socket
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var startThread: Thread? = null
    private var stopThread: Thread? = null
    private var client: Any? = null
    
    companion object {
        private const val TAG = "ProtoVpnService"
        private const val CHANNEL_ID = "dtproto_vpn"
        private const val NOTIFICATION_ID = 1001
        
        // Configurações do servidor XHTTP
        private const val SERVER_HOST = "oracle.koom.pp.ua"      // Host/SNI
        private const val SERVER_PORT = 443                       // Porta
        private const val SERVER_SNI = "oracle.koom.pp.ua"       // SNI
        private const val PROXY_HOST = "oracle.koom.pp.ua"       // 🔥 Proxy/Address
        private const val USE_TLS = true                          // TLS ativado
        private const val USERNAME = ""                           // Sem usuário
        private const val PASSWORD = ""                           // Sem senha
        
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i(TAG, msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        log("📱 DTProto XHTTP VPN")
        log("📍 Host: $SERVER_HOST:$SERVER_PORT")
        log("🔐 SNI: $SERVER_SNI")
        log("🔄 Proxy: $PROXY_HOST")
        log("🔒 TLS: $USE_TLS")
        log("👤 Auth: Nenhuma")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
        
        startThread?.interrupt()
        startThread = thread(name = "dtproto-start") {
            try {
                log("⏳ Iniciando DTProto Client...")
                
                // Configuração COMPLETA do DTProto
                val cfg = DTProtoClientConfig().apply {
                    host = SERVER_HOST
                    port = SERVER_PORT
                    sni = SERVER_SNI
                    proxyHost = PROXY_HOST    // 🔥 Campo Proxy/Address
                    tls = USE_TLS
                    username = USERNAME
                    password = PASSWORD
                    
                    keepAliveInterval = 120
                    keepAliveMaxRetry = 5
                    reconnectDelay = 3
                }
                
                log("⚙️ Config: $SERVER_HOST:$SERVER_PORT")
                log("   SNI: $SERVER_SNI | Proxy: $PROXY_HOST | TLS: $USE_TLS")
                
                // TunBuilder
                val tunBuilder = TunInterfaceBuilder { ip ->
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
                
                // SocketOpener com protect()
                val socketOpener = SocketOpener {
                    val socket = Socket()
                    if (!protect(socket)) {
                        socket.close()
                        throw IllegalStateException("VpnService.protect falhou")
                    }
                    ParcelFileDescriptor.fromSocket(socket).detachFd().toLong()
                }
                
                // Status Listener
                val statusListener = StatusListener { status, error ->
                    log("📊 $status")
                    updateNotification(status ?: "desconhecido")
                    
                    when (status) {
                        "CONNECTING" -> log("⏳ Conectando...")
                        "AUTHENTICATING" -> log("🔐 Autenticando...")
                        "HANDSHAKING" -> log("🤝 Handshake...")
                        "OPENING_TUN" -> log("🔧 Criando TUN...")
                        "CONNECTED" -> log("✅ VPN CONECTADA!")
                        "DISCONNECTED" -> log("🔌 Desconectado")
                        "ERROR" -> {
                            log("❌ ERRO: ${error?.message}")
                            error?.printStackTrace()
                        }
                    }
                }
                
                // Log Handler
                val logHandler = LogHandler { level, _, message ->
                    runCatching { 
                        Log.println(level.toInt(), TAG, message.orEmpty()) 
                    }
                }
                
                // Criar e iniciar cliente
                client = LibDTProto.new_(cfg, tunBuilder, socketOpener, statusListener, logHandler)
                (client as? LibDTProto)?.start()
                
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
        log("🛑 Encerrando...")
        stopThread?.interrupt()
        stopThread = thread(name = "dtproto-stop") {
            try { (client as? LibDTProto)?.stop() }
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
