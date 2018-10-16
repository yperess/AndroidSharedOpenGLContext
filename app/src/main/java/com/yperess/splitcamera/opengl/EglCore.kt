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

package com.yperess.splitcamera.opengl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.text.TextUtils.split
import android.view.Surface
import timber.log.Timber
import javax.microedition.khronos.egl.EGL
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL

/**
 *
 */
class EglCore(
    private val eglConfigChooser: GLSurfaceView.EGLConfigChooser,
    private val eglContextFactory: GLSurfaceView.EGLContextFactory,
    private val eglWindowSurfaceFactory: GLSurfaceView.EGLWindowSurfaceFactory,
    private val glWrapper: GLSurfaceView.GLWrapper?
) {

    companion object {
        fun formatEglError(function: String, error: Int): String =
                "$function failed: ${getErrorString(error)}"

        fun getErrorString(error: Int): String = when (error) {
            EGL10.EGL_SUCCESS -> "EGL_SUCCESS"
            EGL10.EGL_NOT_INITIALIZED -> ""
            EGL10.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
            EGL10.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
            EGL10.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
            EGL10.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
            EGL10.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
            EGL10.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
            EGL10.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
            EGL10.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
            EGL10.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
            EGL10.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
            EGL10.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
            EGL10.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
            EGL11.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST"
            else -> "0x${error.toString(16)}"
        }
    }
    private var egl: EGL10? = null
    private var eglDisplay: EGLDisplay? = null
    var eglConfig: EGLConfig? = null
        private set
    private var eglContext: EGLContext? = null

    private val queryValues = IntArray(1)

    val isStarted: Boolean
        get() = eglDisplay != null && eglConfig != null && eglContext != null

    fun start() {
        Timber.d("start()")
        val egl = egl ?: (EGLContext.getEGL() as EGL10).also {
            egl = it
        }
        val display = eglDisplay ?: egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY).also {
            eglDisplay = it
        }
        if (display == EGL10.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL display")
        }
        val config = eglConfig ?: run {
            val version = IntArray(2)
            egl.eglInitialize(display, version)
            eglConfigChooser.chooseConfig(egl, display)
        }.also {
            eglConfig = it
        }
        if (eglContext == null) {
            eglContext = eglContextFactory.createContext(egl, display, config)
            if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
                throw RuntimeException("createContext failed")
            }
        }
        egl.eglQueryString(eglDisplay, EGL10.EGL_VERSION).let { eglVersion ->
            Timber.d("EGLContext created, egl version = %s", eglVersion)
            val eglVersionInts = eglVersion.substringBefore(" ")
                    .split(".").map { it.toInt() }
            if (eglVersionInts.size != 2) return@let
            if (eglVersionInts[0] > 1 || eglVersionInts[1] >= 4) {
                val values = IntArray(1)
                egl.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values)
                Timber.d("EGLContext created, client version = %d", values[0])
            }
        }
    }

    fun finish() {
        Timber.d("finish()")
        eglContext?.let {
            eglContextFactory.destroyContext(egl, eglDisplay, it)
            eglContext = null
        }
        eglDisplay?.let {
            egl?.eglTerminate(it)
            eglDisplay = null
        }
    }

    fun createWindowSurface(nativeWindow: Any): EGLSurface {
        if (nativeWindow !is Surface && nativeWindow !is SurfaceTexture) {
            throw RuntimeException("invalid surface: $nativeWindow")
        }
        val egl = requireEgl()

        val eglSurface = eglWindowSurfaceFactory.createWindowSurface(egl, eglDisplay, eglConfig,
                nativeWindow)
        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            throw RuntimeException("failed to create EGL surface: ${egl.eglGetError()}")
        }
        return eglSurface
    }

    fun releaseSurface(surface: EGLSurface) {
        requireEgl().eglDestroySurface(eglDisplay, surface)
    }

    fun makeDefaultCurrent() {
        if (!requireEgl().eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                        eglContext)) {
            throw RuntimeException("eglMakeCurrent failed for default NO_SURFACE")
        }
    }

    fun makeCurrent(surface: EGLSurface) {
        if (!requireEgl().eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun swap(surface: EGLSurface): Int {
        val egl = requireEgl()
        if (!egl.eglSwapBuffers(eglDisplay, surface)) {
            return egl.eglGetError()
        }
        return EGL10.EGL_SUCCESS
    }

    fun createGL(): GL = eglContext!!.gl!!.let { glWrapper?.wrap(it) ?: it }

    @Synchronized
    fun querySurface(eglSurface: EGLSurface, what: Int): Int {
        requireEgl().eglQuerySurface(eglDisplay, eglSurface, what, queryValues)
        return queryValues[0]
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Private helper methods

    private fun requireEgl(): EGL10 = egl ?: throw IllegalStateException("egl must not be null")
}