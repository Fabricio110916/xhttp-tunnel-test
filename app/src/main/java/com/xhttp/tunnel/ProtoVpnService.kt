package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.*
import java.net.*
import javax.net.ssl.*
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var startThread: Thread? = null
    private var stopThread: Thread? = null
    
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
    
    // ============================================================
    // RECOMENDAÇÃO DO README: Criar canal ANTES de startForeground
    // ============================================================
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        log("?? ProtoVpnService criado")
    }
    
    // ============================================================
    // RECOMENDAÇÃO DO README: Apenas UMA instância por sessão
    // ============================================================
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("?? onStartCommand")
        
        // Iniciar foreground (recomendação do README)
        startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
        
        // Garantir apenas UMA thread (recomendação)
        startThread?.interrupt()
        startThread = thread(name = "dtproto-start") {
            try {
                log("⏳ Iniciando conexão...")
                runVpn()
            } catch (e: Exception) {
                log("❌ Erro fatal: ${e.message}")
                e.printStackTrace()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    // ============================================================
    // MÉTODO PRINCIPAL - Segue a estrutura do README
    // ============================================================
    private fun runVpn() {
        try {
            // PASSO 1: Criar socket TLS
            log("[1/5] Criando socket TLS...")
            val socket = Socket()
            
            // ?? RECOMENDAÇÃO DO README: protect() o socket!
            if (!protect(socket)) {
                socket.close()
                throw IllegalStateException("VpnService.protect falhou")
            }
            log("✅ Socket protegido!")
            
            // Conectar ao servidor
            socket.connect(InetSocketAddress("168.138.147.212", 443), 10000)
            
            // TLS
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            val factory = ctx.socketFactory
            val tlsSocket = factory.createSocket(socket, "168.138.147.212", 443, true) as SSLSocket
            tlsSocket.startHandshake()
            log("✅ TLS: ${tlsSocket.session.cipherSuite}")
            
            // PASSO 2: Enviar POST
            log("[2/5] Enviando POST...")
            val w = OutputStreamWriter(tlsSocket.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            
            val r = BufferedReader(InputStreamReader(tlsSocket.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
            }
            log("✅ POST OK")
            
            // PASSO 3: Criar TUN (igual README)
            log("[3/5] Criando interface TUN...")
            val builder = Builder()
                .setSession("DTProto")  // Nome igual ao README
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")  // DNS igual ao README
                .addDnsServer("8.8.8.8")
                .addRoute("168.138.147.212", 32)  // Excluir servidor
                .addRoute("0.0.0.0", 0)
            
            val vpnFd = builder.establish() 
                ?: throw IllegalStateException("Falha ao criar TUN")
            
            // ?? RECOMENDAÇÃO DO README: detachFd() para performance
            val fdLong = vpnFd.detachFd().toLong()
            log("✅ TUN criada! FD: $fdLong")
            
            // PASSO 4: Status CONNECTED (igual README)
            log("[4/5] Túnel ativo!")
            updateNotification("Conectado!")
            
            // PASSO 5: Encaminhamento
            log("[5/5] ?? VPN ATIVA!")
            log("?? IP: 10.8.0.2")
            log("?? Ícone de VPN deve aparecer!")
            
            // Encaminhamento com socket protegido
            val tlsIn = tlsSocket.inputStream
            val tlsOut = tlsSocket.outputStream
            
            // Upload: TUN -> TLS
            thread(name = "Upload") {
                try {
                    val vpnIn = FileInputStream(vpnFd.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    var total = 0L
                    while (true) {
                        len = vpnIn.read(buf)
                        if (len > 0) {
                            tlsOut.write(buf, 0, len)
                            tlsOut.flush()
                            total += len
                        }
                    }
                } catch (e: Exception) {
                    log("?? Upload finalizado")
                }
            }
            
            // Download: TLS -> TUN
            thread(name = "Download") {
                try {
                    val vpnOut = FileOutputStream(vpnFd.fileDescriptor)
                    val buf = ByteArray(32768)
                    var len: Int
                    var total = 0L
                    while (true) {
                        len = tlsIn.read(buf)
                        if (len > 0) {
                            vpnOut.write(buf, 0, len)
                            vpnOut.flush()
                            total += len
                        }
                    }
                } catch (e: Exception) {
                    log("?? Download finalizado")
                }
            }
            
        } catch (e: Exception) {
            log("❌ FALHA: ${e.message}")
            throw e
        }
    }
    
    // ============================================================
    // RECOMENDAÇÃO DO README: finalizar com client.stop()
    // ============================================================
    override fun onDestroy() {
        log("?? onDestroy - Finalizando VPN...")
        stopThread?.interrupt()
        stopThread = thread(name = "dtproto-stop") {
            try {
                // Cleanup
            } catch (e: Exception) {
                log("Erro ao parar: ${e.message}")
            }
        }
        super.onDestroy()
    }
    
    // ============================================================
    // RECOMENDAÇÃO DO README: Canal de notificação
    // ============================================================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DTProto VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("DTProto VPN")
        .setContentText(content)
        .setOngoing(true)
        .build()
    
    private fun updateNotification(status: String) {
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            buildNotification("Estado: $status")
        )
    }
    
    // ============================================================
    // RECOMENDAÇÃO DO README: SocketOpener com protect()
    // ============================================================
    private fun createProtectedSocket(): Socket {
        val socket = Socket()
        if (!protect(socket)) {
            socket.close()
            throw IllegalStateException("VpnService.protect falhou")
        }
        return socket
    }
    
    class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
