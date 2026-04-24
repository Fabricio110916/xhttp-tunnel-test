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
            startForeground(NOTIFICATION_ID, createNotification("SOCKS5 ativo"))
            Thread { startTunnel() }.start()
        }
        return START_STICKY
    }
    
    private fun startTunnel() {
        isRunning = true
        
        try {
            log("[1/3] Conectando túnel XHTTP...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket("168.138.147.212", 443) as SSLSocket
            tlsSocket?.soTimeout = 0
            tlsSocket?.startHandshake()
            
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) { if (line!!.isEmpty()) break }
            log("✅ Túnel XHTTP OK")
            
            log("[2/3] Iniciando SOCKS5...")
            proxyServer = ServerSocket(PROXY_PORT)
            log("✅ SOCKS5 em 127.0.0.1:$PROXY_PORT")
            
            updateNotification("SOCKS5 Proxy", "Porta $PROXY_PORT")
            
            log("[3/3] ?? Proxy ativo!")
            log("?? 127.0.0.1:$PROXY_PORT")
            log("?? Configure apps para usar este proxy")
            
            while (isRunning) {
                try {
                    val client = proxyServer?.accept() ?: break
                    Thread { handleSocks5(client) }.start()
                } catch (e: Exception) {
                    if (isRunning) log("⚠️ ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            log("❌ ${e.message}")
            stopSelf()
        }
    }
    
    private fun handleSocks5(client: Socket) {
        try {
            val cin = client.getInputStream()
            val cout = client.getOutputStream()
            val tin = tlsSocket!!.inputStream
            val tout = tlsSocket!!.outputStream
            
            // ==========================================
            // HANDSHAKE SOCKS5 CORRETO (RFC 1928)
            // ==========================================
            
            // 1. Ler versão e métodos
            val ver = cin.read()
            val nmethods = cin.read()
            val methods = ByteArray(nmethods)
            cin.read(methods)
            
            log("?? SOCKS5: ver=$ver, métodos=$nmethods")
            
            // 2. Responder: sem autenticação (0x00)
            cout.write(byteArrayOf(5, 0))
            cout.flush()
            
            // 3. Ler requisição
            val reqVer = cin.read()
            val cmd = cin.read()
            val rsv = cin.read() // 0x00
            val atyp = cin.read()
            
            // 4. Resolver endereço
            val dstAddr: String
            when (atyp) {
                1 -> { // IPv4
                    val addr = ByteArray(4)
                    cin.read(addr)
                    dstAddr = InetAddress.getByAddress(addr).hostAddress
                }
                3 -> { // Domain
                    val len = cin.read()
                    val domain = ByteArray(len)
                    cin.read(domain)
                    dstAddr = String(domain)
                }
                4 -> { // IPv6
                    val addr = ByteArray(16)
                    cin.read(addr)
                    dstAddr = InetAddress.getByAddress(addr).hostAddress
                }
                else -> {
                    log("❌ SOCKS5: ATYP desconhecido $atyp")
                    client.close()
                    return
                }
            }
            
            val dstPort = ((cin.read() and 0xFF) shl 8) or (cin.read() and 0xFF)
            
            log("?? SOCKS5: CONNECT $dstAddr:$dstPort")
            
            // 5. Responder sucesso
            val response = byteArrayOf(
                5,      // VER
                0,      // REP (sucesso)
                0,      // RSV
                1,      // ATYP (IPv4)
                0, 0, 0, 0,  // BND.ADDR (0.0.0.0)
                0, 0    // BND.PORT (0)
            )
            cout.write(response)
            cout.flush()
            
            // 6. Encaminhamento bidirecional
            val t1 = Thread {
                try {
                    val buf = ByteArray(8192)
                    var len: Int
                    while (isRunning) {
                        len = cin.read(buf)
                        if (len <= 0) break
                        tout.write(buf, 0, len)
                        tout.flush()
                    }
                } catch (e: Exception) {}
            }
            
            val t2 = Thread {
                try {
                    val buf = ByteArray(8192)
                    var len: Int
                    while (isRunning) {
                        len = tin.read(buf)
                        if (len <= 0) break
                        cout.write(buf, 0, len)
                        cout.flush()
                    }
                } catch (e: Exception) {}
            }
            
            t1.start()
            t2.start()
            t1.join()
            t2.join()
            
        } catch (e: Exception) {
            log("❌ SOCKS5 erro: ${e.message}")
        } finally {
            try { client.close() } catch (e: Exception) {}
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
                NotificationChannel(CHANNEL_ID, "XHTTP SOCKS5", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
    
    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("XHTTP SOCKS5").setContentText(text)
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
