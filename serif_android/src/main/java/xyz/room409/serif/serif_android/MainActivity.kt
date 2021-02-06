package xyz.room409.serif.serif_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import xyz.room409.serif.serif_shared.MatrixClient
import android.widget.TextView

fun version(): String {
    return MatrixClient().version()
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv: TextView = findViewById(R.id.text_view)
        tv.text = version()
    }
}
