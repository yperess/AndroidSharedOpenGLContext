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

import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.EGL10

/**
 *
 */
class EglSurfaceWrapper(
    private val eglCore: EglCore,
    private val surface: Any,
    private var _width: Int,
    private var _height: Int
) : EglReentrantLock() {

    private var eglSurface = EGL10.EGL_NO_SURFACE

    val width: Int
        get() = _width
    val height: Int
        get() = _height
    var sizeChanged = true
    var requestRelease = false

    val surfaceWidth: Int
        get() = if (eglSurface == EGL10.EGL_NO_SURFACE) -1
            else eglCore.querySurface(eglSurface, EGL10.EGL_WIDTH)

    val surfaceHeight: Int
        get() = if (eglSurface == EGL10.EGL_NO_SURFACE) -1
        else eglCore.querySurface(eglSurface, EGL10.EGL_HEIGHT)

    val hasEglSurface: Boolean
        get() = eglSurface != EGL10.EGL_NO_SURFACE

    fun setSize(width: Int, height: Int) {
        if (width == _width && height == _height) {
            return
        }
        _width = width
        _height = height
        sizeChanged = true
    }

    fun createWindowSurface(): Boolean {
        if (eglSurface == EGL10.EGL_NO_SURFACE) {
            eglSurface = eglCore.createWindowSurface(surface)
            return true
        }
        return false
    }

    fun release() {
        if (eglSurface != EGL10.EGL_NO_SURFACE) {
            eglCore.releaseSurface(eglSurface)
            eglSurface = EGL10.EGL_NO_SURFACE
        }
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swap(): Int = eglCore.swap(eglSurface)

    override fun toString(): String = StringBuilder("EglSurfaceWrapper@")
            .append(hashCode().toString(16))
            .append("(")
            .append("hasEglSurface=").append(hasEglSurface)
            .append(",width=").append(width)
            .append(",height=").append(height)
            .append(",sizeChanged=").append(sizeChanged)
            .append(",requestRelease=").append(requestRelease)
            .append(")")
            .toString()
}