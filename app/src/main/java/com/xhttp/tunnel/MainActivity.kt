package com.xhttp.tunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import java.net.*
import javax.net.ssl.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    
    private val handler = Handler(Looper.getMainLooper())
    private val VPN_REQUEST_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.terminalText)
        scrollView = findViewById(R.id.terminalScroll)
        
        TunnelService.logCallback = { msg ->
            handler.post {
                logText.append("$msg\n")
                scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
        
        startButton.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                startTunnel()
            }
        }
        
        stopButton.setOnClickListener {
            stopService(Intent(this, TunnelService::class.java))
            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Parado"
        }
        
        log("?? XHTTP VPN v2")
        log("?? 168.138.147.212:443")
        log("")
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startTunnel()
        }
    }
    
    private fun startTunnel() {
        startService(Intent(this, TunnelService::class.java))
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.text = "VPN Ativa"
    }
    
    private fun log(msg: String) {
        handler.post {
            logText.append("$msg\n")
            scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }
    
    class TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
