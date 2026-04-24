package com.xhttp.tunnel

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    
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
            startService(Intent(this, TunnelService::class.java))
            startButton.isEnabled = false
            stopButton.isEnabled = true
            statusText.text = "SOCKS5 Ativo"
        }
        
        stopButton.setOnClickListener {
            stopService(Intent(this, TunnelService::class.java))
            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Parado"
        }
        
        logText.text = "?? XHTTP SOCKS5 Proxy\n?? 168.138.147.212:443\n?? 127.0.0.1:1080\n\n"
    }
}
