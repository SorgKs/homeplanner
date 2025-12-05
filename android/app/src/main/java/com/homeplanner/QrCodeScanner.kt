package com.homeplanner

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * QR Code Scanner Composable using CameraX and ML Kit.
 */
@Composable
fun QrCodeScannerDialog(
    onScanResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Request camera permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            errorMessage = "Разрешение на камеру необходимо для сканирования QR-кода"
        }
    }
    
    LaunchedEffect(Unit) {
        val permission = android.Manifest.permission.CAMERA
        val hasPermissionNow = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasPermissionNow) {
            hasPermission = true
        } else {
            permissionLauncher.launch(permission)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasPermission) {
            Text(
                text = errorMessage ?: "Запрос разрешения на камеру...",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        } else {
            QrCodeScannerView(
                context = context,
                lifecycleOwner = lifecycleOwner,
                onScanResult = { result ->
                    onScanResult(result)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun QrCodeScannerView(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onScanResult: (String) -> Unit
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProvider = cameraProviderFuture.get()
            
            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            preview = previewUseCase
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                processImageProxy(barcodeScanner, imageProxy, onScanResult)
            }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("QrCodeScanner", "Use case binding failed", e)
            }
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: androidx.camera.core.ImageProxy,
    onScanResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    when (barcode.valueType) {
                        Barcode.TYPE_TEXT, Barcode.TYPE_URL -> {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                Log.d("QrCodeScanner", "Scanned QR code: $rawValue")
                                onScanResult(rawValue)
                                return@addOnSuccessListener
                            }
                        }
                        else -> {
                            Log.d("QrCodeScanner", "Unsupported barcode type: ${barcode.valueType}")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("QrCodeScanner", "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

