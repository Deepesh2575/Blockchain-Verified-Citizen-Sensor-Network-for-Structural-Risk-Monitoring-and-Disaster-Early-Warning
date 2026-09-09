package com.sih26223.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ArEscapeRouteActivity : AppCompatActivity() {

    private lateinit var sceneView: ARSceneView
    private lateinit var tvArStatus: TextView
    private lateinit var btnExitAr: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_escape_route)

        sceneView = findViewById(R.id.sceneView)
        tvArStatus = findViewById(R.id.tvArStatus)
        btnExitAr = findViewById(R.id.btnExitAr)

        btnExitAr.setOnClickListener {
            finish()
        }

        // Simulate detecting a path and overlaying arrows
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            tvArStatus.text = "Floor detected. Follow the glowing arrows to West Exit."
            
            // In a real implementation:
            // sceneView.addChild(ModelNode(modelInstance = ...))
            // attached to an Anchor on the AR plane.
        }
    }
}
