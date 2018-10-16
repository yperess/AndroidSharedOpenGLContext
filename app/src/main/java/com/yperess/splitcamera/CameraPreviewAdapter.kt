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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yperess.splitcamera.opengl.GLThread

/**
 *
 */
class CameraPreviewAdapter(
    private val count: Int,
    private val glThread: GLThread
) : RecyclerView.Adapter<CameraPreviewViewHolder>() {

    private var width = 0
    private var height = 0

    fun setPreviewSize(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            notifyDataSetChanged()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // RecyclerView.Adapter

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraPreviewViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_camera_preview, parent, false)
        return CameraPreviewViewHolder(view, glThread)
    }

    override fun getItemCount(): Int = count

    override fun onBindViewHolder(holder: CameraPreviewViewHolder, position: Int) {
        holder.bind(width, height)
    }
}