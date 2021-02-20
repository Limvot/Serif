package xyz.room409.serif.serif_android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import xyz.room409.serif.serif_shared.MatrixLogin
import xyz.room409.serif.serif_shared.MatrixState

fun version(mstate: MatrixState): String {
    return mstate.version + ", Android UI"
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv: TextView = findViewById(R.id.text_view)
        var mstate: MatrixState = MatrixLogin()
        tv.text = version(mstate)
    }
}
