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
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 *
 */
class DefaultContextFactory(
    eglClientVersion: Int = 2
) : GLSurfaceView.EGLContextFactory {

    companion object {
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        fun getAtribList(contextClientVersion: Int): IntArray =
                intArrayOf(EGL_CONTEXT_CLIENT_VERSION, contextClientVersion, EGL10.EGL_NONE)
    }

    private val attribList = getAtribList(eglClientVersion)

    override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext =
            egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attribList)

    override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
        egl.eglDestroyContext(display, context)
    }
}