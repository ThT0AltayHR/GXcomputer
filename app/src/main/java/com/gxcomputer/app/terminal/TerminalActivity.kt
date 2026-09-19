package com.gxcomputer.app.terminal

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gxcomputer.app.R
import com.gxcomputer.app.container.GraphicsDriverConfig
import com.gxcomputer.app.container.StorageHelper

class TerminalActivity : AppCompatActivity() {

    private lateinit var session: ShellSession
    private lateinit var outputText: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var commandInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val storage = StorageHelper(this)
        session = ShellSession(storage, GraphicsDriverConfig(this, storage))

        outputText = findViewById(R.id.outputText)
        outputScroll = findViewById(R.id.outputScroll)
        commandInput = findViewById(R.id.commandInput)

        appendOutput("GXcomputer Uçbirimi - container: ${storage.containerDir().path}\n")

        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                runCurrentCommand()
                true
            } else false
        }
    }

    private fun runCurrentCommand() {
        val cmd = commandInput.text.toString().trim()
        if (cmd.isEmpty()) return
        appendOutput("gx> $cmd")
        commandInput.text.clear()

        Thread {
            val result = session.run(cmd)
            runOnUiThread { appendOutput(result) }
        }.apply { isDaemon = true }.start()
    }

    private fun appendOutput(text: String) {
        outputText.append(if (outputText.text.isEmpty()) text else "\n$text")
        outputScroll.post { outputScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
