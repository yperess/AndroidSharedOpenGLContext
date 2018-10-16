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

import android.opengl.GLSurfaceView
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.withLock

/**
 *
 */
class GLThread(
    private val eglConfigChooser: GLSurfaceView.EGLConfigChooser,
    private val eglContextFactory: GLSurfaceView.EGLContextFactory,
    private val eglWindowSurfaceFactory: GLSurfaceView.EGLWindowSurfaceFactory,
    private val glWrapper: GLSurfaceView.GLWrapper?,
    private val initializer: GLInitializer
) : Thread() {

    companion object {
        private val glThreadManager = EglReentrantLock()
    }

    interface GLInitializer {
        fun onGLContextCreated(gl: GL10)
        fun onPreDraw(gl: GL10)
    }

    enum class RenderMode { CONTINUOUS, WHEN_DIRTY }

    private val eglCore = EglCore(eglConfigChooser, eglContextFactory, eglWindowSurfaceFactory,
            glWrapper)
    private val renderers = ConcurrentHashMap<GLSurfaceView.Renderer, EglSurfaceWrapper>()
    private var requestPaused = false
    private var paused = false
    private var requestRender = false
    private var renderComplete = false
    private var requestExit = false
    private var exited = false

    var renderMode = RenderMode.CONTINUOUS
        private set
    var preserveEglContextOnPause = true
        private set
    private var lostEglContext = false
    private var initialized = false

    fun setPreserveEglContextOnPause(preserve: Boolean): GLThread {
        preserveEglContextOnPause = preserve
        return this
    }

    fun setRenderMode(renderMode: RenderMode): GLThread {
        this.renderMode = renderMode
        return this
    }

    fun onSurfaceCreated(renderer: GLSurfaceView.Renderer, surface: Any, width: Int, height: Int) {
        glThreadManager.withLock {
            renderers.put(renderer, EglSurfaceWrapper(eglCore, surface, width, height))
            glThreadManager.signalAll()
        }
    }

    fun onSurfaceChanged(renderer: GLSurfaceView.Renderer, width: Int, height: Int) {
        renderers[renderer]?.withLockThenSignalAll<EglSurfaceWrapper,Unit> {
            setSize(width, height)
        }
    }

    fun onSurfaceDestroyed(renderer: GLSurfaceView.Renderer) {
        renderers[renderer]?.withLockThenSignalAll<EglSurfaceWrapper,Unit> {
            requestRelease = true
        }
    }

    fun onResume() {
        glThreadManager.withLock {
            requestPaused = false
            requestRender = true
            renderComplete = false
            spinLocked { !exited && paused && !renderComplete }
        }
    }

    fun onPause() {
        glThreadManager.withLock {
            requestPaused = true
            glThreadManager.signalAll()
            spinLocked { !exited && !paused }
        }
    }

    fun requestExitAndWait() {
        glThreadManager.withLock {
            requestExit = true
            glThreadManager.signalAll()
            spinLocked { !exited }
        }
    }

    override fun run() {
        name = "GLThread $id"
        try {
            while (guardedRun()) {}
        } catch (ex: InterruptedException) {
            // Fall through and exit normally
        } finally {
            glThreadManager.withLock {
                releaseEglSurfacesLocked()
                releaseEglContextLocked()
            }
            eglCore.finish()
            exited = true
//            glThreadManager.signalAll()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Private helper methods

    /**
     * @return True to continue looping
     */
    private fun guardedRun(): Boolean {
        glThreadManager.withLock {
            if (requestExit) {
                return false
            }

            var pausing = false
            if (paused != requestPaused) {
                pausing = requestPaused
                paused = requestPaused
                glThreadManager.signalAll()
            }

            if(pausing) {
                releaseEglSurfacesLocked()
                if (eglCore.isStarted && !preserveEglContextOnPause) {
                    releaseEglContextLocked()
                }
            }

            if (lostEglContext) {
                releaseEglSurfacesLocked()
                releaseEglContextLocked()
                lostEglContext = false
            }

            if (readyToDraw) {
                // If we don't have an egl context try to acquire one
                if (!eglCore.isStarted) {
                    try {
                        eglCore.start()
                    } finally {
                        glThreadManager.signalAll()
                    }
                }
                renderers.forEachWithLock { _, eglSurfaceWrapper ->
                    if (eglSurfaceWrapper.sizeChanged) {
                        eglSurfaceWrapper.release()
                    }
                }
            } else {
                glThreadManager.await()
                return true
            }
        }
        val gl10 = eglCore.createGL() as GL10
        if (!initialized) {
            eglCore.makeDefaultCurrent()
            initialized = true
            initializer.onGLContextCreated(gl10)
        }

        var ranPreDraw = false
        renderers.forEachWithLock { renderer, eglSurfaceWrapper ->
            if (eglSurfaceWrapper.requestRelease) {
                eglSurfaceWrapper.release()
                return@forEachWithLock
            }
            val windowCreated = eglSurfaceWrapper.createWindowSurface()
            if (windowCreated) {
                if (!ranPreDraw) {
                    eglCore.makeDefaultCurrent()
                    ranPreDraw = true
                    initializer.onPreDraw(gl10)
                }
                eglSurfaceWrapper.makeCurrent()
                renderer.onSurfaceCreated(gl10, eglCore.eglConfig)
            }
            if (!eglSurfaceWrapper.hasEglSurface) {
                // Error, no surface
                Timber.w("Missing surface")
                return@forEachWithLock
            }
            eglSurfaceWrapper.makeCurrent()
            if (eglSurfaceWrapper.sizeChanged) {
                renderer.onSurfaceChanged(gl10, eglSurfaceWrapper.width, eglSurfaceWrapper.height)
                eglSurfaceWrapper.sizeChanged = false
            }
            renderer.onDrawFrame(gl10)
            val swapError = eglSurfaceWrapper.swap()
            when (swapError) {
                EGL10.EGL_SUCCESS -> {}
                EGL11.EGL_CONTEXT_LOST -> {
                    Timber.i("egl context lost")
                    lostEglContext = true
                }
                else -> {
                    Timber.w(EglCore.formatEglError("eglSwapBuffers", swapError))
                    eglSurfaceWrapper.requestRelease = true
                    glThreadManager.signalAll()
                }
            }
        }
        return true
    }

    private fun spinLocked(message: String? = null, condition: ()->Boolean) {
        while (condition()) {
            message?.let { Timber.i(it) }
            try {
                glThreadManager.await()
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun releaseEglSurfacesLocked() {
        renderers.values.forEach { eglWrapper ->
            eglWrapper.release()
        }
    }

    private fun releaseEglContextLocked() {
        if (eglCore.isStarted) {
            eglCore.finish()
            glThreadManager.signalAll()
        }
    }

    private val readyToDraw: Boolean
        get() = !paused && (requestRender || renderMode == RenderMode.CONTINUOUS)

    private inline fun <K, reified V: EglReentrantLock> ConcurrentHashMap<K, V>.forEachWithLock(
        block: (renderer: K, eglSurfaceWrapper: V)->Unit
    ) {
        keys.forEach { key ->
            get(key)?.withLockThenSignalAll<V,Unit> {
                block(key, this)
            }
        }
    }
}