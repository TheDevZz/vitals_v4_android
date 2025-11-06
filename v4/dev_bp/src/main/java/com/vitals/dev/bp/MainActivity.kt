package com.vitals.dev.bp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.vitals.dev.bp.databinding.ActivityMainBinding
import com.vitals.lib.Port
import com.vitals.sdk.internal.CryptoUtils.decryptStream
import kotlinx.coroutines.GlobalScope

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        viewBinding.root.postDelayed(::runTest, 1000)
    }

    private fun runTest() {
        // assets.open("bp.pt.bin").use {
        //     Port.storeBinaryData("bp.pt", decryptStream(it, "255afad56fc64068dcc4d30dc6d660075931de288abda60019e0e71493443cb9"))
        // }
        // Port.nativeRunTest(Port.copyBPModels(this))
    }
}