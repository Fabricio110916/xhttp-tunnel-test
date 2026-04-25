package com.xhttp.tunnel

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
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        startButton = findViewById(R.id.startButton)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.terminalText)
        scrollView = findViewById(R.id.terminalScroll)
        
        startButton.setOnClickListener {
            startButton.isEnabled = false
            statusText.text = "Testando..."
            Thread { testConnection() }.start()
        }
        
        log("?? TESTE DE CONEXÃO BÁSICA")
        log("?? oracle.koom.pp.ua:443")
        log("")
    }
    
    private fun log(msg: String) {
        handler.post {
            logText.append("$msg\n")
            scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }
    
    private fun testConnection() {
        try {
            log("1️⃣ Testando DNS...")
            val addr = InetAddress.getByName("oracle.koom.pp.ua")
            log("✅ DNS: ${addr.hostAddress}")
            
            log("2️⃣ Testando TCP...")
            val socket = Socket()
            socket.connect(InetSocketAddress("168.138.147.212", 443), 5000)
            log("✅ TCP conectado")
            
            log("3️⃣ Testando TLS...")
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(TrustAll()), java.security.SecureRandom())
            val tls = ctx.socketFactory.createSocket(socket, "168.138.147.212", 443, true) as SSLSocket
            tls.startHandshake()
            log("✅ TLS: ${tls.session.cipherSuite}")
            
            log("4️⃣ Enviando POST...")
            val w = OutputStreamWriter(tls.outputStream)
            w.write("POST /ssh HTTP/1.1\r\nHost: oracle.koom.pp.ua\r\nContent-Length: 0\r\n\r\n")
            w.flush()
            
            log("5️⃣ Lendo resposta...")
            val r = BufferedReader(InputStreamReader(tls.inputStream))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                log("   $line")
                if (line!!.isEmpty()) break
            }
            
            log("")
            log("════════════════════════════")
            log("✅ TODOS OS TESTES PASSARAM!")
            log("════════════════════════════")
            
            tls.close()
            
        } catch (e: Exception) {
            log("❌ ERRO: ${e.message}")
        }
        
        handler.post {
            startButton.isEnabled = true
            statusText.text = "Teste concluído"
        }
    }
    
    class TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
