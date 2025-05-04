package com.example.bestsplit

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onClose: () -> Unit,
    onQrCodeDetected: (String, Double) -> Unit,
    amount: Double = 0.0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // State for camera permission
    var hasCameraPermission by remember { mutableStateOf(false) }

    // Permission request launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                Toast.makeText(
                    context,
                    "Camera permission is required to scan QR codes",
                    Toast.LENGTH_LONG
                ).show()
                onClose()
            }
        }
    )

    // Check permission initially
    LaunchedEffect(Unit) {
        hasCameraPermission = checkCameraPermission(context)
        if (!hasCameraPermission) {
            Log.d("QRScanner", "Requesting camera permission")
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } else {
            Log.d("QRScanner", "Camera permission already granted")
        }
    }

    // State for payment verification dialog
    var showPaymentVerificationDialog by remember { mutableStateOf(false) }
    var detectedQrCode by remember { mutableStateOf("") }

    // Keep references updated
    val currentOnQrDetected = rememberUpdatedState(onQrCodeDetected)
    val currentOnClose = rememberUpdatedState(onClose)

    // Clean up when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (!hasCameraPermission) {
            // Show message when no permission
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is required to scan QR codes")
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        try {
                            startCamera(
                                context = ctx,
                                lifecycleOwner = lifecycleOwner,
                                previewView = previewView,
                                cameraExecutor = cameraExecutor,
                                onDetected = { barcode ->
                                    Log.d("QRScanner", "QR Code detected: $barcode")
                                    detectedQrCode = barcode

                                    // Process the QR code and initiate payment
                                    val upiDetails = UpiPaymentUtils.parseUpiQrCode(barcode)
                                    if (upiDetails != null) {
                                        Log.d(
                                            "QRScanner",
                                            "UPI details parsed: ${upiDetails.upiId}"
                                        )

                                        // Close camera immediately 
                                        currentOnClose.value()

                                        // Use UpiPaymentUtils to initiate payment
                                        UpiPaymentUtils.initiateUpiPayment(
                                            context = context,
                                            upiId = upiDetails.upiId,
                                            amount = amount,
                                            description = "BestSplit Settlement",
                                            transactionRef = "BestSplit${System.currentTimeMillis()}"
                                        )

                                        // Show payment verification dialog after a short delay
                                        // to allow user to complete the payment
                                        scope.launch {
                                            delay(2000) // 2 second delay
                                            showPaymentVerificationDialog = true
                                        }
                                    } else {
                                        // Invalid QR code
                                        Toast.makeText(
                                            context,
                                            "Invalid UPI QR code",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            Log.e("QRScanner", "Error starting camera", e)
                            Toast.makeText(
                                ctx,
                                "Could not start camera: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            // Close the scanner if camera cannot start
                            currentOnClose.value()
                        }
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .size(250.dp)
                            .align(Alignment.Center),
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, Color.White)
                    ) {}

                    Text(
                        "Position QR code in the frame",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        color = Color.White
                    )
                }
            }
        }
    }

    // Payment verification dialog
    if (showPaymentVerificationDialog) {
        QrPaymentVerificationDialog(
            onConfirm = {
                // Process the QR code and complete the payment flow
                currentOnQrDetected.value(detectedQrCode, amount)
                showPaymentVerificationDialog = false
                currentOnClose.value()

                // Show confirmation toast
                Toast.makeText(
                    context,
                    "Payment confirmed and recorded successfully",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                // Cancel the payment flow
                showPaymentVerificationDialog = false
                Toast.makeText(
                    context,
                    "Payment not confirmed. You can try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun QrPaymentVerificationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Payment Verification",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Did you complete the payment successfully?",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "The settlement will only be recorded if you confirm the payment was successful.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.End
                    )
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("No")
                    }

                    Button(onClick = onConfirm) {
                        Text("Yes")
                    }
                }
            }
        }
    }
}

private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: ExecutorService,
    onDetected: (String) -> Unit
) {
    // Double check permission before starting camera
    if (!checkCameraPermission(context)) {
        Log.e("QRScanner", "Camera permission not granted")
        return
    }

    try {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        // Add timeout to avoid hanging indefinitely
        val timeoutRunnable = Runnable {
            Log.e("QRScanner", "Camera initialization timeout")
            Toast.makeText(
                context,
                "Camera initialization timed out. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }

        val timeoutHandler = Handler(Looper.getMainLooper())
        timeoutHandler.postDelayed(timeoutRunnable, 10000) // 10 second timeout

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let { code ->
                                Log.d("QRScanner", "QR Code detected: $code")
                                onDetected(code)
                            }
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()

                    // Cancel timeout as we've successfully reached this point
                    timeoutHandler.removeCallbacks(timeoutRunnable)

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                    Log.d("QRScanner", "Camera bound successfully")
                } catch (ex: Exception) {
                    Log.e("QRScanner", "Camera binding failed", ex)
                    throw ex
                }

            } catch (ex: Exception) {
                Log.e("QRScanner", "Camera setup failed", ex)
                throw ex
            }
        }, ContextCompat.getMainExecutor(context))
    } catch (ex: Exception) {
        Log.e("QRScanner", "Camera setup failed", ex)
        throw ex
    }
}

private class QRCodeAnalyzer(
    private val onQRCodesDetected: (List<Barcode>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        onQRCodesDetected(barcodes)
                        imageProxy.close()
                        return@addOnSuccessListener
                    }
                }
                .addOnFailureListener {
                    Log.e("QRScanner", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

private fun checkCameraPermission(context: Context): Boolean {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    Log.d("QRScanner", "Camera permission check: $hasPermission")
    return hasPermission
}