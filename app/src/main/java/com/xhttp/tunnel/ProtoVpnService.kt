package com.xhttp.tunnel

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.util.Log
import kotlin.concurrent.thread

class ProtoVpnService : VpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 999
        var logCallback: ((String) -> Unit)? = null
    }
    
    private fun log(msg: String) {
        Log.i("ProtoVPN", msg)
        logCallback?.invoke(msg)
    }
    
    override fun onCreate() {
        super.onCreate()
        log("?? onCreate")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopVpn(); return START_NOT_STICKY }
        if (!isRunning) {
            thread { startVpn() }
        }
        return START_STICKY
    }
    
    private fun startVpn() {
        isRunning = true
        
        try {
            log("[1/2] Criando VPN MÍNIMA...")
            val builder = Builder()
                .setSession("ProtoVPN")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
            
            log("   Chamando establish()...")
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                log("❌ establish() retornou NULL!")
                throw Exception("VPN null")
            }
            
            log("✅ VPN CRIADA! Descritor: $vpnInterface")
            log("[2/2] ?? VPN ATIVA!")
            log("?? IP: 10.8.0.2")
            log("?? Ícone de VPN deve aparecer!")
            log("⏸ Aguardando 30s...")
            
            for (i in 1..30) {
                if (!isRunning) break
                Thread.sleep(1000)
                if (i % 10 == 0) log("   ⏰ ${i}s")
            }
            
            if (isRunning) log("✅ VPN ESTÁVEL POR 30 SEGUNDOS!")
            
        } catch (e: Exception) {
            log("❌ ERRO: ${e.message}")
            e.printStackTrace()
            stopVpn()
        }
    }
    
    private fun stopVpn() {
        isRunning = false
        try { vpnInterface?.close() } catch(e: Exception) {}
        stopSelf()
        log("⏹ VPN parada")
    }
    
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
