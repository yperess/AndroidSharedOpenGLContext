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

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *
 */
open class EglReentrantLock: ReentrantLock() {
    private val condition: Condition by lazy { newCondition() }

    fun signalAll() {
        condition.signalAll()
    }

    fun signal() {
        condition.signal()
    }

    fun await() {
        condition.await()
    }

    inline fun <reified T: EglReentrantLock, R> withLockThenSignalAll(body: T.()->R): R {
        (this as? T) ?: throw RuntimeException("Can't get lock for ${T::class.java.simpleName}")
        this.withLock {
            val result = body(this)
            signalAll()
            return result
        }
    }
}