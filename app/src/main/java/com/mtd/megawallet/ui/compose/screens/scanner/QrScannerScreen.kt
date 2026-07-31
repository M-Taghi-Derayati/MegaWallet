package com.mtd.megawallet.ui.compose.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors


private enum class ScanStatus { Scanning, Invalid, Found }

@Composable
fun QrScannerScreen(
    onClose: () -> Unit,
    onAddressScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var status by remember { mutableStateOf(ScanStatus.Scanning) }
    var torchOn by remember { mutableStateOf(false) }
    // نگهدارندهٔ camera برای کنترلِ فلاش؛ آرایه‌ی تک‌عضوی چون از داخلِ AndroidView مقدار می‌گیرد.
    val cameraHolder = remember { arrayOfNulls<androidx.camera.core.Camera>(1) }
    // یک‌بارمصرف: بعد از یافتنِ آدرسِ معتبر دیگر پردازش نکن.
    val handled = remember { mutableStateOf(false) }
    val onScanned by rememberUpdatedState(onAddressScanned)

    BackHandler { onClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    Executors.newSingleThreadExecutor(),
                                    QrCodeAnalyzer { raw ->
                                        if (handled.value) return@QrCodeAnalyzer
                                        val address = extractCryptoAddress(raw)
                                        if (address != null) {
                                            handled.value = true
                                            status = ScanStatus.Found
                                            onScanned(address)
                                        } else {
                                            status = ScanStatus.Invalid
                                        }
                                    }
                                )
                            }
                        provider.unbindAll()
                        cameraHolder[0] = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        cameraHolder[0]?.cameraControl?.enableTorch(torchOn)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // به‌روزرسانیِ فلاش هنگام toggle
            LaunchedEffect(torchOn) { cameraHolder[0]?.cameraControl?.enableTorch(torchOn) }

            ScannerOverlay(status = status)
        } else {
            PermissionRequest(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        // نوار بالا: بستن + فلاش
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
            }
            if (hasPermission) {
                IconButton(
                    onClick = { torchOn = !torchOn },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "فلاش",
                        tint = Color.White
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // اطمینان از آزادسازیِ دوربین هنگام بسته شدن
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

@Composable
private fun ScannerOverlay(status: ScanStatus) {
    // رنگِ براکت بر اساس وضعیت
    val bracketColor by animateColorAsState(
        targetValue = when (status) {
            ScanStatus.Scanning -> Color.White
            ScanStatus.Found -> Color(0xFF34C759)
            ScanStatus.Invalid -> Color(0xFFFF3B30)
        },
        label = "bracketColor"
    )

    // پیشرفتِ انیمیشنِ «جمع شدن» گوشه‌ها به سمت مرکز (فقط موقع Found)
    val collapse by animateFloatAsState(
        targetValue = if (status == ScanStatus.Found) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "collapse"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ---------- لایهٔ تصویر: scrim + براکت‌ها ----------
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = (size.width * 0.68f).coerceAtMost(size.height * 0.5f)
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val left = cx - side / 2f
            val top = cy - side / 2f

            // مقادیرِ «مؤثر» پس از اعمالِ انیمیشنِ جمع‌شدن
            val inset = (side / 2f) * collapse
            val eLeft = left + inset
            val eTop = top + inset
            val eSide = (side - 2f * inset).coerceAtLeast(1f)

            val cornerR = 24.dp.toPx()                 // شعاعِ قوسِ گوشه (مثل عکس)
            val eR = (cornerR * (1f - collapse)).coerceAtLeast(0f)
            val baseLen = side * 0.16f
            val eLen = baseLen * (1f - collapse)       // بازوها هم موقع جمع‌شدن کوتاه می‌شن
            val stroke = 5.dp.toPx()

            // اسکریمِ تیره با سوراخِ مربعِ گِرد — آلفا کم تا شبیه عکس (برای حذف کامل: 0f)
            val windowRect = Rect(Offset(eLeft, eTop), Size(eSide, eSide))
            val scrim = Path().apply {
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(windowRect, CornerRadius(eR, eR)))
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.15f))

            // براکتِ گوشه‌ها با قوس (quadraticBezier) — دقیقاً حسِ عکس
            fun corner(px: Float, py: Float, dx: Int, dy: Int, ln: Float, rad: Float) {
                val r = rad.coerceAtMost(ln * 0.9f).coerceAtLeast(0f)
                val path = Path().apply {
                    moveTo(px + dx * ln, py)            // سرِ بازوی افقی
                    lineTo(px + dx * r, py)             // تا آستانهٔ قوس
                    quadraticBezierTo(px, py, px, py + dy * r) // قوسِ گوشه
                    lineTo(px, py + dy * ln)            // سرِ بازوی عمودی
                }
                drawPath(path, bracketColor, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            corner(eLeft, eTop, 1, 1, eLen, eR)
            corner(eLeft + eSide, eTop, -1, 1, eLen, eR)
            corner(eLeft, eTop + eSide, 1, -1, eLen, eR)
            corner(eLeft + eSide, eTop + eSide, -1, -1, eLen, eR)
        }

        // ---------- لایهٔ متن ----------
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val side = minOf(maxWidth * 0.68f, maxHeight * 0.5f)
            val cy = maxHeight * 0.42f

            // متنِ وسطِ کادر (مثل عکس) — موقع Found محو می‌شه و متنِ تأیید جایگزین می‌شه
            Box(
                modifier = Modifier.fillMaxWidth().height(cy),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "اسکن کد QR",
                    color = Color.White.copy(alpha = 1f - collapse),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "✓ آدرس خوانده شد",
                    color = Color(0xFF34C759).copy(alpha = collapse),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // راهنما + پیامِ خطا، درست زیرِ کادر
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = cy + side / 2f + 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "آدرس کیف‌پول را داخل کادر بگیرید",
                        color = Color.White.copy(alpha = 0.75f * (1f - collapse)),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    AnimatedVisibility(
                        visible = status == ScanStatus.Invalid,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "کد نامعتبر است — یک آدرس کیف‌پول اسکن کنید",
                            color = Color(0xFFFF6B6B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "برای اسکنِ آدرس، دسترسی به دوربین لازم است",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRequest,
            modifier = Modifier.padding(top = 20.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
        ) {
            Text("اجازه دسترسی به دوربین", color = Color.White)
        }
    }
}

/** آنالایزرِ ML Kit فقط برای QR. */
private class QrCodeAnalyzer(private val onRaw: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let(onRaw)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/**
 * از محتوای QR یک آدرسِ کریپتو استخراج می‌کند (یا null اگر آدرس نباشد).
 * پیشوندهای URI مثل `ethereum:`، `bitcoin:`، `tron:` و پارامترهای `?...`/`@chain` حذف می‌شوند.
 * تشخیصِ نهاییِ شبکه در SendViewModel انجام می‌شود؛ این‌جا فقط برای UXِ «معتبر/نامعتبر» است.
 */
private fun extractCryptoAddress(raw: String): String? {
    var s = raw.trim()
    val colon = s.indexOf(':')
    if (colon in 1..12 && !s.startsWith("0x", ignoreCase = true)) {
        val scheme = s.substring(0, colon).lowercase()
        if (scheme in setOf("ethereum", "bitcoin", "tron", "litecoin", "dogecoin", "bnb", "ether")) {
            s = s.substring(colon + 1)
        }
    }
    s = s.substringBefore('?').substringBefore('@').trim()
    return when {
        Regex("^0x[0-9a-fA-F]{40}$").matches(s) -> s                       // EVM
        Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$").matches(s) -> s               // Tron
        Regex("^(bc1|tb1)[0-9ac-hj-np-z]{20,80}$").matches(s) -> s          // BTC bech32
        Regex("^[13][1-9A-HJ-NP-Za-km-z]{25,39}$").matches(s) -> s          // BTC legacy
        Regex("^D[1-9A-HJ-NP-Za-km-z]{25,40}$").matches(s) -> s             // Dogecoin
        else -> null
    }
}
