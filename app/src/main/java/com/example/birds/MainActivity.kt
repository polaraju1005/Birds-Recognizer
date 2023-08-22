package com.example.birds

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import com.example.birds.databinding.ActivityMainBinding
import com.example.birds.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: CardView
    private lateinit var output: TextView
    private val GALLERY_REQUEST_CODE = 123
    private lateinit var button2:CardView

    @SuppressLint("IntentReset")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imgUpload
        button = binding.btnCapture
        output = binding.txtOutput

        button2 = binding.btnUpload

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
        button2.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        //to redirect user to google search for the  scientific name of the bird
        output.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${output.text}")
            )
            startActivity(intent)
        }
        // to download image when longPress on ImageView
        imageView.setOnLongClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePicturePreview.launch(null)
            } else {
                Toast.makeText(this, "Permission Denied !!", Toast.LENGTH_SHORT).show()
            }
        }

    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                outputGenerator(bitmap)
            }
        }

    private val onresult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")
            onResultReceived(GALLERY_REQUEST_CODE, result)
        }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap =
                            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        val birdsModel = BirdsModel.newInstance(this)

        // converting bitmap into tensorflow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfimage = TensorImage.fromBitmap(newBitmap)

        // process the image using trained model and sort it in descending order
        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }
        //getting result having high probability
        val highProbabilityOutput = outputs[0]

        //setting output text
        output.text = highProbabilityOutput.label
        Log.i("TAG", "outputGenerator:$highProbabilityOutput")
    }

    // to download image to device
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                AlertDialog.Builder(this).setTitle("Download Image")
                    .setMessage("Do you want to download this image to your device?")
                    .setPositiveButton("Yes") {_, _->
                        val drawable:BitmapDrawable = imageView.drawable as BitmapDrawable
                        val bitmap = drawable.bitmap
                        downloadImage(bitmap)
                    }
                    .setNegativeButton("No"){dialog, _->
                        dialog.dismiss()
                    }
                    .show()
            }else{
                Toast.makeText(this,"Please allow permission to download image",Toast.LENGTH_SHORT).show()
            }
        }
    // to download image to device
    private fun downloadImage(mBitmap: Bitmap):Uri?{
        val contentValues = ContentValues().apply{
            put(MediaStore.Images.Media.DISPLAY_NAME,"Birds_Images"+System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null){
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG,100,outputStream)){
                        throw IOException("Couldn't save the bitmap")
                    }
                    else{
                        Toast.makeText(applicationContext,"Image saved",Toast.LENGTH_SHORT).show()
                    }
                }
                return it
            }
        }
        return null
    }
}