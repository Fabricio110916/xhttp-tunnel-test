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
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private val VPN_REQUEST_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        startButton = findViewById(R.id.startButton)
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
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                startVpn()
            }
        }
        
        logText.text = "?? TESTE VPN MÍNIMA\n(sem TLS, sem túnel)\n\n"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVpn()
        }
    }
    
    private fun startVpn() {
        startService(Intent(this, ProtoVpnService::class.java))
        startButton.isEnabled = false
        statusText.text = "VPN Ativa"
    }
}
