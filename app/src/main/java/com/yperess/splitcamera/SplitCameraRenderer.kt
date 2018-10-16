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
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.yperess.splitcamera.opengl.GLThread
import timber.log.Timber
import javax.microedition.khronos.opengles.GL10

/**
 *
 */
class SplitCameraRenderer(
    private val onSurfaceTextureChangedListener: OnSurfaceTextureChangedListener
) : GLThread.GLInitializer, SurfaceTexture.OnFrameAvailableListener {

    interface OnSurfaceTextureChangedListener {
        fun onSurfaceTextureChanged(newTexture: SurfaceTexture, width: Int, height: Int)
    }

    private val textures = IntArray(1)
    private var isAllocated = false
    private var previewWidth = -1
    private var previewHeight = -1
    private var surfaceTexture: SurfaceTexture? = null
    private var isDirty = false

    fun setPreviewSize(width: Int, height: Int) {
        if (previewWidth != width || previewHeight != height) {
            previewWidth = width
            previewHeight = height
            createSurfaceTexture()
        }
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // GLThread.GLInitializer

    override fun onGLContextCreated(gl: GL10) {
        Timber.d("onGLContextCreated()")
        // Allocate the texture that will capture the camera
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(textures.size, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER,
                GL10.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER,
                GL10.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S,
                GL10.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T,
                GL10.GL_CLAMP_TO_EDGE)

        createSurfaceTexture()
    }

    override fun onPreDraw(gl: GL10) {
        synchronized(this) {
            if (isDirty) {
                surfaceTexture?.updateTexImage()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // SurfaceTexture.OnFrameAvailableListener

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            isDirty = true
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Private helper methods

    private fun createSurfaceTexture() {
        if (!isAllocated || previewWidth < 0 || previewHeight < 0) {
            return
        }
        Timber.d("creating %dx%d SurfaceTexture", previewWidth, previewHeight)
        surfaceTexture?.release()
        surfaceTexture = SurfaceTexture(textures[0]).apply {
            setDefaultBufferSize(previewWidth, previewHeight)
            setOnFrameAvailableListener(this@SplitCameraRenderer)
            onSurfaceTextureChangedListener.onSurfaceTextureChanged(this, previewWidth, previewHeight)
        }
    }
}