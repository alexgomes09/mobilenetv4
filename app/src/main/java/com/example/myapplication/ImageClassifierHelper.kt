/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.myapplication

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis

class ImageClassifierHelper(
    val context: Context,
    val classifierListener: ClassifierListener? = null
) {
    private var interpreter: InterpreterApi? = null
    private val numClasses = 1000 // Number of classes in ImageNet
    private val classes: MutableList<String> = mutableListOf()

    init {
        try {
            loadClassLabels()
            interpreter = Interpreter(loadModelFile())
            interpreter?.allocateTensors()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun loadClassLabels() {
        val string = context.assets.open("imagenet_class_index.json")
            .bufferedReader().use {
                it.readText()
            }

        JSONObject(string).also {
            it.keys().forEach { key ->
                it.optJSONArray(key).also { array ->
                    if (array != null) {
                        classes.add(array.optString(1))
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("mobilenetv4_conv_small.e2400_r224_in1k_float16.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(input: Array<Array<Array<FloatArray>>>) {

        val output = Array(1) { FloatArray(numClasses) }

        val time = measureTimeMillis {
            interpreter?.run(input, output)
        }

        val topResult = getTopResult(output[0])

        classifierListener?.onResults(topResult, time)
    }

    fun getTopResult(output: FloatArray, topK: Int = 3): List<Pair<String, Float>> {
        return output.mapIndexed { index, score -> index to score}
            .sortedByDescending { it.second }
            .take(topK)
            .map { classes[it.first] to it.second }
    }

    interface ClassifierListener {
        fun onResults(
            results: List<Pair<String,Float>>?,
            inferenceTime: Long
        )
    }
}
