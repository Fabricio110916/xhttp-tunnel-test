package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class XHttpVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
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
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification("VPN Teste"))
            thread { startVpn() }
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            log("════════════════════════════════")
            log("?? TESTE: APENAS VPN (sem túnel)")
            log("════════════════════════════════")
            
            log("[1/2] Criando VPN mínima...")
            val builder = Builder()
                .setSession("XHTTP VPN Teste")
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
            
            log("   Chamando establish()...")
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                log("❌ establish() retornou NULL!")
                throw Exception("VPN null")
            }
            
            log("[2/2] ✅ VPN CRIADA COM SUCESSO!")
            log("   Descritor: $vpnInterface")
            log("   ?? Ícone de VPN deve aparecer!")
            
            updateNotification("VPN ATIVA", "Aguardando 30s...")
            
            // Aguardar 30 segundos
            for (i in 1..30) {
                if (!isRunning) break
                Thread.sleep(1000)
            }
            
            if (isRunning) {
                log("")
                log("════════════════════════════════")
                log("✅ TESTE CONCLUÍDO!")
                log("   A VPN NÃO CRASHOU!")
                log("   O problema é o TLS/Túnel!")
                log("════════════════════════════════")
            }
            
        } catch (e: Exception) {
            log("❌ FALHA: ${e.message}")
            e.printStackTrace()
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        try { vpnInterface?.close() } catch (e: Exception) {}
        stopForeground(true)
        stopSelf()
        log("⏹ VPN parada")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "XHTTP VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XHTTP VPN Teste")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
