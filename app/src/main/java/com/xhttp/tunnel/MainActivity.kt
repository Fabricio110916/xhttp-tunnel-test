package com.xhttp.tunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var forceStopButton: Button
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
        forceStopButton = findViewById(R.id.forceStopButton)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.terminalText)
        scrollView = findViewById(R.id.terminalScroll)
        
        ProtoVpnService.logCallback = { msg ->
            handler.post {
                logText.append("$msg\n")
                scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }
        
        startButton.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, VPN_REQUEST_CODE)
            else startVpn()
        }
        
        stopButton.setOnClickListener {
            stopService(Intent(this, ProtoVpnService::class.java).apply { action = "STOP" })
            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Parado"
        }
        
        forceStopButton.setOnClickListener {
            stopService(Intent(this, ProtoVpnService::class.java))
            startButton.isEnabled = true
            stopButton.isEnabled = false
            forceStopButton.isEnabled = false
            statusText.text = "Parada forçada"
        }
        
        logText.text = "?? ProtoVPN\n?? 168.138.147.212:443\n\n"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) startVpn()
    }
    
    private fun startVpn() {
        startService(Intent(this, ProtoVpnService::class.java))
        startButton.isEnabled = false
        stopButton.isEnabled = true
        forceStopButton.isEnabled = true
        statusText.text = "VPN Ativa"
    }
}
