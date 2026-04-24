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
    
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var sniInput: EditText
    private lateinit var proxyInput: EditText
    private lateinit var tlsSwitch: Switch
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
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
        
        hostInput = findViewById(R.id.hostInput)
        portInput = findViewById(R.id.portInput)
        sniInput = findViewById(R.id.sniInput)
        proxyInput = findViewById(R.id.proxyInput)
        tlsSwitch = findViewById(R.id.tlsSwitch)
        userInput = findViewById(R.id.userInput)
        passInput = findViewById(R.id.passInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
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
        
        stopButton.setOnClickListener {
            stopService(Intent(this, ProtoVpnService::class.java))
            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Parado"
        }
        
        logText.text = "?? DTProto XHTTP VPN\nConfigure os campos e conecte\n\n"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVpn()
        }
    }
    
    private fun startVpn() {
        val intent = Intent(this, ProtoVpnService::class.java).apply {
            putExtra("host", hostInput.text.toString())
            putExtra("port", portInput.text.toString())
            putExtra("sni", sniInput.text.toString())
            putExtra("proxy", proxyInput.text.toString())
            putExtra("tls", tlsSwitch.isChecked)
            putExtra("username", userInput.text.toString())
            putExtra("password", passInput.text.toString())
        }
        startService(intent)
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.text = "Conectando..."
    }
}
