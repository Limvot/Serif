package xyz.room409.serif.serif_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import xyz.room409.serif.serif_shared.MatrixState
import xyz.room409.serif.serif_shared.MatrixLogin
import android.widget.TextView

fun version(mstate : MatrixState): String {
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
