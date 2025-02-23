package com.alishoumar.androidstorage

import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.alishoumar.androidstorage.presentation.adapter.InternalStoragePhotoAdapter
import com.alishoumar.androidstorage.presentation.adapter.SharedStoragePhotoAdapter
import com.alishoumar.androidstorage.presentation.adapter.SpaceItemDecoration
import com.alishoumar.androidstorage.databinding.ActivityMainBinding
import com.alishoumar.androidstorage.presentation.ExternalStorageViewModel
import com.alishoumar.androidstorage.presentation.InternalStorageViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
    private lateinit var externalStoragePhotoAdapter: SharedStoragePhotoAdapter
    private lateinit var contentObserver : ContentObserver
    private lateinit var itemDecoration : SpaceItemDecoration
    private lateinit var registerPermissions: ActivityResultLauncher<Array<String>>
    private val internalStorageViewModel: InternalStorageViewModel by viewModels()
    private val externalStorageViewModel: ExternalStorageViewModel by viewModels()
    private var deletedPhotoUri:Uri? = null
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root){v , insets->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        itemDecoration = SpaceItemDecoration(16)

         intentSenderLauncher =registerForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
            callback = {
                if (it.resultCode == RESULT_OK) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        lifecycleScope.launch {
                            deletePhotoFromExternalStorage(deletedPhotoUri ?: return@launch)
                        }
                        Toast.makeText(this, "Photo Deleted Successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            internalStorageViewModel.deletePhotoFromInternalStorage(it.name)
        }

        externalStoragePhotoAdapter = SharedStoragePhotoAdapter {
            lifecycleScope.launch {
                deletePhotoFromExternalStorage(it.uri)
                deletedPhotoUri = it.uri
            }
        }

        registerPermissions = registerForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            callback = {}
        )


        setUpRecyclerViews()
        setUpObservables()
        initContentObserver()


        val takePhoto = registerForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview(),
            callback = {
                lifecycleScope.launch {
                    if(binding.switchPrivate.isChecked){
                        internalStorageViewModel.savePhotoToInternalStorage(
                            UUID.randomUUID().toString(),
                            it!!)
                    }else{
                        externalStorageViewModel.savePhotoToExternalStorage(
                            UUID.randomUUID().toString(), it!!
                        )
                    }
                }
            }
        )
        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }
    }

    private fun setUpRecyclerViews(){
        binding.rvPrivatePhotos.apply{
            adapter = internalStoragePhotoAdapter
            layoutManager = StaggeredGridLayoutManager(3,RecyclerView.VERTICAL)
            addItemDecoration(itemDecoration)
        }
        binding.rvPublicPhotos.apply{
            adapter = externalStoragePhotoAdapter
            layoutManager = StaggeredGridLayoutManager(3,RecyclerView.VERTICAL)
            addItemDecoration(itemDecoration)

        }
    }
    private fun setUpObservables(){
        externalStorageViewModel.unGrantedPermissions.observe(this){
            if(it.isNotEmpty())
                registerPermissions.launch(it.toTypedArray())
        }
        internalStorageViewModel.internalPhotos.observe(this) {
            internalStoragePhotoAdapter.submitList(it)
        }
        externalStorageViewModel.externalStoragePhotos.observe(this){
            externalStoragePhotoAdapter.submitList(it)
        }
    }

    private suspend fun deletePhotoFromExternalStorage(photoUri: Uri){
        withContext (Dispatchers.IO){
            externalStorageViewModel.deletePhotoFromExternalStorage(photoUri)?.let {sender ->
                intentSenderLauncher.launch(
                    IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }

    private fun initContentObserver(){
        contentObserver = object : ContentObserver(null){
            override fun onChange(selfChange: Boolean) {
                externalStorageViewModel.loadPhotosFromExternalStorage()
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}