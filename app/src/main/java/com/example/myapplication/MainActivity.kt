package com.example.myapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.*
import java.net.CacheRequest
import java.util.*
import kotlin.collections.HashMap

//QGR5T5-RQQEYU33WT
class MainActivity : AppCompatActivity() {
    val TAG: String = "111111"
    lateinit var p_bar: ProgressBar
    lateinit var pods1Adapter: SimpleAdapter
    lateinit var waEngine: WAEngine
    lateinit var requestInput: TextInputEditText
    val pods = mutableListOf<HashMap<String, String>>()
    lateinit var textToSpeech: TextToSpeech
    var isTtsReady = false
    val REC_CODE = 555


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        initWolframEng()
        initTts()
    }

    fun initViews() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val voiceInpBut: FloatingActionButton = findViewById(R.id.voice_inp_button)
        voiceInpBut.setOnClickListener {
           pods.clear()
            pods1Adapter.notifyDataSetChanged()
            if (isTtsReady){
                textToSpeech.stop()
            }
            showVoiceInput()
        }
        p_bar = findViewById(R.id.p_bar)

        val podsList: ListView = findViewById(R.id.pods_list)
        pods1Adapter = SimpleAdapter(
            applicationContext, pods, R.layout.item_pod, arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )
        podsList.adapter = pods1Adapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            val title = pods[position]["Title"]
            val content = pods[position]["Content"]
            textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
        }


        requestInput = findViewById(R.id.text_input_edit)
        requestInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pods.clear()
                pods1Adapter.notifyDataSetChanged()
                val question = requestInput.text.toString()
                askWolfram(question)
            }
            return@setOnEditorActionListener false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stop -> {
                if (isTtsReady) {
                    textToSpeech.stop()
                }
                return true
            }
            R.id.action_clear -> {
                requestInput.text?.clear()
                pods.clear()
                pods1Adapter.notifyDataSetChanged()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun initWolframEng() {
        waEngine = WAEngine().apply {
            appID = "QGR5T5-RQQEYU33WT"
            addFormat("plaintext")
        }
    }

    fun showSnackBar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE)
            .apply {
                setAction(android.R.string.ok) {
                    dismiss()
                }
                show()
            }
    }

    fun askWolfram(request: String) {
        p_bar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    p_bar.visibility = View.GONE
                    if (result.isError) {
                        showSnackBar(result.errorMessage)
                        return@withContext
                    }
                    if (!result.isSuccess) {
                        requestInput.error =
                            getString(com.google.android.material.R.string.error_icon_content_description)
                        return@withContext
                    }
                    for (pod in result.pods) {
                        if (pod.isError) continue
                        val content = StringBuilder()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                        pods.add(0, HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }
                    pods1Adapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    p_bar.visibility = View.GONE
                    showSnackBar(
                        t.message
                            ?: getString(com.google.android.material.R.string.error_icon_content_description)
                    )
                }
            }
        }
    }

    fun initTts() {
        textToSpeech = TextToSpeech(this) { code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Tts err code: $code")
                showSnackBar(getString(com.google.android.material.R.string.error_icon_content_description))
            } else {
                isTtsReady = true
            }
        }
        textToSpeech.language = Locale.ENGLISH
    }
    fun showVoiceInput(){
        val intent= Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT,getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.US)
        }
        runCatching {
            startActivityForResult(intent,REC_CODE)
        }.onFailure {t->
            showSnackBar(t.message?:getString(com.google.android.material.R.string.error_icon_content_description))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
            if(requestCode==REC_CODE&&resultCode== RESULT_OK){
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let{question->
                    requestInput.setText(question)
                    askWolfram(question)
                }
            }
    }
}