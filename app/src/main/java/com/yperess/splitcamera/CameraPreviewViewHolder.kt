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

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.TextureView
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.yperess.splitcamera.opengl.GLThread
import kotlinx.android.synthetic.main.view_camera_preview.view.*
import timber.log.Timber
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 *
 */
class CameraPreviewViewHolder(
    itemView: View,
    private val glThread: GLThread
) : RecyclerView.ViewHolder(itemView), TextureView.SurfaceTextureListener, GLSurfaceView.Renderer {

    companion object {
        private val RND = Random()
    }
    private var previewWidth = 0
    private var previewHeight = 0

    private val red: Float
        get() = if (adapterPosition % 3 == 0) 1f else 0f
    private val green: Float
        get() = if (adapterPosition % 3 == 1) 1f else 0f
    private val blue: Float
        get() = if (adapterPosition % 3 == 2) 1f else 0f

    fun bind(previewWidth: Int, previewHeight: Int) {
        this.previewWidth = previewWidth
        this.previewHeight = previewHeight
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // GLSurfaceView.Renderer

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(red, green, blue, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("onSurfaceChanged(%s, %d, %d)", gl, width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("onSurfaceCreated(%s, %s)", gl, config)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // TextureView.SurfaceTextureListener

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable(%s, %d, %d)", surface, width, height)
        glThread.onSurfaceCreated(this, surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureSizeChanged(%s, %d, %d)", surface, width, height)
        glThread.onSurfaceChanged(this, width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        Timber.d("onSurfaceTextureDestroyed(%s)", surface)
        glThread.onSurfaceDestroyed(this)
        return true
    }

    init {
        itemView.texture_view.surfaceTextureListener = this
    }
}