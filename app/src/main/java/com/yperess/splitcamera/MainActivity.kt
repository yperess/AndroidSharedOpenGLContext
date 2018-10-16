// Copyright (c) 2018 Yuval Peress. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.yperess.splitcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.yperess.splitcamera.opengl.BaseConfigChooser
import com.yperess.splitcamera.opengl.DefaultContextFactory
import com.yperess.splitcamera.opengl.DefaultWindowSurfaceFactory
import com.yperess.splitcamera.opengl.GLThread
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity(), SplitCameraRenderer.OnSurfaceTextureChangedListener {

    private val renderer = SplitCameraRenderer(this)

    private val glThread = GLThread(BaseConfigChooser.SimpleEGLConfigChooser(true),
            DefaultContextFactory(), DefaultWindowSurfaceFactory(), null, renderer)

    private var camera: Camera? = null

    private val adapter: CameraPreviewAdapter by lazy { CameraPreviewAdapter(10, glThread) }

    private val cameraPreviewSpacing: Int by lazy {
        resources.getDimensionPixelSize(R.dimen.camera_preview_spacing)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recycler_view.adapter = adapter
        recycler_view.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.set(cameraPreviewSpacing, cameraPreviewSpacing, cameraPreviewSpacing,
                        cameraPreviewSpacing)
            }
        })
        glThread.start()
    }

    override fun onStart() {
        super.onStart()
        startCamera()
        glThread.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        camera?.stopPreview()
        glThread.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        camera?.release()
        renderer.release()
        glThread.requestExitAndWait()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 1000) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startCamera()
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // SplitCameraRenderer.OnSurfaceTextureChangedListener

    override fun onSurfaceTextureChanged(newTexture: SurfaceTexture, width: Int, height: Int) {
        Timber.d("onSurfaceTextureChanged(%s, %d, %d)", newTexture, width, height)
        camera?.setPreviewTexture(newTexture)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Private helper methods

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            val camera = camera ?: Camera.open()
            this.camera = camera
            renderer.release()
            camera.parameters.previewSize.let { size ->
                adapter.setPreviewSize(size.width, size.height)
                renderer.setPreviewSize(size.width, size.height)
            }
            camera.startPreview()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1000)
        }
    }
}
