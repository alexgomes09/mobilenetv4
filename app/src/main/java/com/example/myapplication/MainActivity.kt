package com.example.myapplication

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.permissionx.guolindev.PermissionX
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity(), ImageClassifierHelper.ClassifierListener {

    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var imageClassifierHelper: ImageClassifierHelper
    private val imageWidth = 244 * 2
    private val imageHeight = 244 * 2
    private val channelCount = 3
    private var resizeAndNormalizationTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        imageClassifierHelper =
            ImageClassifierHelper(context = this, this)

        activityMainBinding.selectImage.setOnClickListener {
            PermissionX.init(this)
                .permissions(android.Manifest.permission.READ_MEDIA_IMAGES)
                .request { allGranted, grantedList, deniedList ->
                    if (allGranted) {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        Toast.makeText(this, "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResults(results: List<Pair<String, Float>>?, inferenceTime: Long) {

        activityMainBinding.inferenceTime.text = "Inference: ${inferenceTime}ms"
        activityMainBinding.resizeNormalizationTime.text = "Resize & Normalization: ${resizeAndNormalizationTime}ms"
        activityMainBinding.classification.text = "Classification: \n\n" +
                SpannableStringBuilder().also { sb ->
                    results?.forEach {
                        sb.append("\u2022 " + it.first + "\n")
                    }
                }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {

            resizeAndNormalizationTime = 0L

            uriToBitmap(this, uri)?.let { bitmap ->
                var modifiedBitmap = bitmap

                resizeAndNormalizationTime += measureTimeMillis {
                    modifiedBitmap = ThumbnailUtils.extractThumbnail(bitmap, imageWidth, imageHeight, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                    activityMainBinding.image.setImageBitmap(modifiedBitmap)

//                normalizeAndClassify(modifiedBitmap)
                }

                val input = processBitmap(modifiedBitmap, imageWidth, imageHeight)
                imageClassifierHelper.classify(input)
            }

        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    private fun processBitmap(bitmap: Bitmap, imageWidth: Int, imageHeight: Int): Array<Array<Array<FloatArray>>> {
        val input = Array(1) { Array(imageWidth) { Array(imageHeight) { FloatArray(3) } } }

        resizeAndNormalizationTime += measureTimeMillis {

            val mean = arrayOf(0.485F, 0.456F, 0.406F)
            val std = arrayOf(0.229F, 0.224F, 0.225F)


            for (x in 0 until imageWidth) {
                for (y in 0 until imageHeight) {
                    val pixel = bitmap.getPixel(x, y)
                    input[0][x][y][0] = ((pixel shr 16 and 0xFF) / 255.0f) - mean[0] / std[0] // Red channel
                    input[0][x][y][1] = ((pixel shr 8 and 0xFF) / 255.0f) - mean[1] / std[1]  // Green channel
                    input[0][x][y][2] = ((pixel and 0xFF) / 255.0f) - mean[2] / std[2]         // Blue channel
                }
            }
        }
        return input
    }

    /* private fun normalizeAndClassify(bitmap: Bitmap) {

         val floatBuffer = FloatArray(imageWidth * imageHeight * channelCount)
         val mean = arrayOf(0.485F, 0.456F, 0.406F)
         val std = arrayOf(0.229F, 0.224F, 0.225F)

         for (y in 0 until 2) {
             for (x in 0 until imageWidth) {

                 /// Normalize the values so that they fall between 0 and 1.
                 var red: Float
                 var green: Float
                 var blue: Float


                 bitmap.getPixel(x, y).also {
                     red = Color.red(it) / 255F
                     green = Color.green(it) / 255f
                     blue = Color.blue(it) / 255f
                     val alpha = Color.alpha(it)
                 }


                 /// floatBuffer contains only the RGB values. floatBuffer has been linearized/flattened i.e. it's being treated as one single row.
                 /// Center (around zero) and scale the data according to the mean and standard deviation.
                 val baseIndex = (y * imageWidth * channelCount) + (x * channelCount)

                 floatBuffer[baseIndex + 0] = (red - mean[0]) / std[0]
                 floatBuffer[baseIndex + 1] = (green - mean[1]) / std[1]
                 floatBuffer[baseIndex + 2] = (blue - mean[2]) / std[2]
             }
         }

         imageClassifierHelper.classify(floatBuffer)
     }*/


    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        // Obtain the content resolver from the context
        val contentResolver: ContentResolver = context.contentResolver

        // Check the API level to use the appropriate method for decoding the Bitmap
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // For Android P (API level 28) and higher, use ImageDecoder to decode the Bitmap
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGB_565, true)
        } else {
            // For versions prior to Android P, use BitmapFactory to decode the Bitmap
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                Bitmap.createBitmap(BitmapFactory.decodeStream(stream))
            }
            bitmap?.copy(Bitmap.Config.RGB_565, true)
        }
    }


}
