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
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 *
 */
class DefaultWindowSurfaceFactory : GLSurfaceView.EGLWindowSurfaceFactory {

    override fun createWindowSurface(
        egl: EGL10,
        display: EGLDisplay,
        config: EGLConfig,
        nativeWindow: Any
    ): EGLSurface {
        var eglSurface: EGLSurface? = null
        while (eglSurface == null) {
            try {
                eglSurface = egl.eglCreateWindowSurface(display, config, nativeWindow, null)
            } catch (tr: Throwable) {
            } finally {
                if (eglSurface == null) {
                    try {
                        Thread.sleep(10)
                    } catch (ex: InterruptedException) {}
                }
            }
        }
        return eglSurface
    }

    override fun destroySurface(
        egl: EGL10,
        display: EGLDisplay,
        surface: EGLSurface
    ) {
        egl.eglDestroySurface(display, surface)
    }
}