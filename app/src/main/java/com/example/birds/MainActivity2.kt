package com.example.birds

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.RequiresApi
import java.util.logging.Handler

class MainActivity2 : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        supportActionBar!!.hide()

        android.os.Handler().postDelayed( Runnable { nextActivity() },2000)
    }

    private fun nextActivity() {
        startActivity(Intent(this,MainActivity::class.java))
        finish()
    }

}