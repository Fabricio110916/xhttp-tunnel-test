package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import java.io.*
import java.net.*
import javax.net.ssl.*
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tlsSocket: SSLSocket? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("ProtoVPN", msg)
        try { logCallback?.invoke(msg) } catch(e: Exception) {}
    }
    
    override fun onCreate() { 
        super.onCreate()
        log("═══════════════════════════════════")
        log("?? ProtoVPN Service CRIADO")
        log("═══════════════════════════════════")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("?? onStartCommand() chamado")
        log("   action: ${intent?.action ?: "START"}")
        log("   flags: $flags")
        
        if (intent?.action == "STOP") {
            log("?? Ação STOP detectada!")
            stopVpn()
            return START_NOT_STICKY
        }
        
        if (!isRunning) {
            log("▶ Iniciando VPN em thread separada...")
            thread(name = "VPN-Thread") { startVpn() }
        } else {
            log("⚠️ VPN já está rodando!")
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            // ================================================================
            // PASSO 1: DNS + TCP
            // ================================================================
            log("")
            log("═══════════════════════════════════")
            log(" PASSO 1/4: CONEXÃO DE REDE")
            log("═══════════════════════════════════")
            
            log("?? Resolvendo DNS: oracle.koom.pp.ua...")
            val addr = InetAddress.getByName("oracle.koom.pp.ua")
            log("   ✅ DNS resolvido: ${addr.hostAddress}")
            
            log("?? Conectando TCP em ${addr.hostAddress}:443...")
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(addr.hostAddress, 443), 10000)
            log("   ✅ TCP conectado!")
            log("   ?? Local: ${rawSocket.localAddress}:${rawSocket.localPort}")
            log("   ?? Remoto: ${rawSocket.inetAddress}:${rawSocket.port}")
            
            // ================================================================
            // PASSO 2: TLS
            // ================================================================
            log("")
            log("═══════════════════════════════════")
            log(" PASSO 2/4: HANDSHAKE TLS")
            log("═══════════════════════════════════")
            
            log("?? Iniciando handshake TLS...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAll()), java.security.SecureRandom())
            tlsSocket = ctx.socketFactory.createSocket(rawSocket, "oracle.koom.pp.ua", 443, true) as SSLSocket
            tlsSocket?.startHandshake()
            log("   ✅ TLS estabelecido!")
            log("   ?? Cipher: ${tlsSocket?.session?.cipherSuite}")
            log("   ?? Protocolo: ${tlsSocket?.session?.protocol}")
            
            // ================================================================
            // PASSO 3: POST /ssh
            // ================================================================
            log("")
            log("═══════════════════════════════════")
            log(" PASSO 3/4: AUTENTICAÇÃO XHTTP")
            log("═══════════════════════════════════")
            
            log("?? Enviando POST /ssh...")
            val w = OutputStreamWriter(tlsSocket!!.outputStream)
            w.write("POST /ssh HTTP/1.1\r\n")
            w.write("Host: oracle.koom.pp.ua\r\n")
            w.write("Content-Length: 0\r\n")
            w.write("Connection: keep-alive\r\n\r\n")
            w.flush()
            log("   ✅ POST enviado!")
            
            log("?? Aguardando resposta...")
            val r = BufferedReader(InputStreamReader(tlsSocket!!.inputStream))
            var line: String?
            var statusLine = ""
            while (r.readLine().also { line = it } != null) {
                log("   ?? $line")
                if (line!!.startsWith("HTTP/")) statusLine = line!!
                if (line!!.isEmpty()) break
            }
            log("   ✅ Resposta: $statusLine")
            
            // ================================================================
            // PASSO 4: VPN
            // ================================================================
            log("")
            log("═══════════════════════════════════")
            log(" PASSO 4/4: CRIAÇÃO DA VPN")
            log("═══════════════════════════════════")
            
            log("?? Configurando interface TUN...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
            
            log("   ?? Session: ProtoVPN")
            log("   ?? IP: 10.8.0.2/32")
            log("   ?? Rota: 0.0.0.0/0")
            log("   ?? DNS: 1.1.1.1, 8.8.8.8")
            log("   ?? MTU: 1500")
            
            log("   ⏳ Chamando establish()...")
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                log("   ❌ establish() retornou NULL!")
                throw Exception("VPN null")
            }
            
            log("   ✅ VPN CRIADA COM SUCESSO!")
            log("   ?? Descritor: $vpnInterface")
            log("")
            log("═══════════════════════════════════")
            log(" ?? VPN ATIVA!")
            log("═══════════════════════════════════")
            log(" ?? IP Virtual: 10.8.0.2")
            log(" ?? Ícone de VPN: DEVE aparecer!")
            log(" ?? Túnel TLS: ativo")
            log(" ?? Cipher: ${tlsSocket?.session?.cipherSuite}")
            log("")
            log(" ⏸ Monitorando conexão...")
            log("")
            
            // Monitoramento
            var segundos = 0
            while (isRunning) {
                Thread.sleep(1000)
                segundos++
                
                if (segundos % 10 == 0) {
                    log("⏰ ${segundos}s | VPN: ATIVA | TLS: ${if (tlsSocket?.isConnected == true) "CONECTADO" else "DESCONECTADO"}")
                }
                
                if (segundos >= 120) {
                    log("")
                    log("═══════════════════════════════════")
                    log(" ✅ VPN ESTÁVEL POR 120 SEGUNDOS!")
                    log("═══════════════════════════════════")
                    break
                }
            }
            
        } catch (e: Exception) {
            log("")
            log("═══════════════════════════════════")
            log(" ❌ FALHA NA CONEXÃO")
            log("═══════════════════════════════════")
            log("   Erro: ${e.message}")
            log("   Tipo: ${e.javaClass.simpleName}")
            e.printStackTrace()
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        log("")
        log("?? PARANDO VPN...")
        isRunning = false
        
        try { 
            tlsSocket?.close()
            log("   ✅ TLS socket fechado")
        } catch(e: Exception) {
            log("   ⚠️ Erro ao fechar TLS: ${e.message}")
        }
        
        try { 
            vpnInterface?.close()
            log("   ✅ Interface VPN fechada")
        } catch(e: Exception) {
            log("   ⚠️ Erro ao fechar VPN: ${e.message}")
        }
        
        tlsSocket = null
        vpnInterface = null
        
        stopForeground(true)
        stopSelf()
        log("   ✅ Serviço parado completamente")
        log("")
    }
    
    override fun onDestroy() { 
        log("?? onDestroy()")
        stopVpn()
        super.onDestroy() 
    }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
