package com.coulterpeterson.floatnative.ui.screens.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.coulterpeterson.floatnative.utils.QrCodeUtils
import com.coulterpeterson.floatnative.viewmodels.TvLoginState
import com.coulterpeterson.floatnative.viewmodels.TvLoginViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: TvLoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Background Gradients
    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF1E3A8A).copy(alpha = 0.3f), // Blue
            Color.Transparent
        ),
        center = androidx.compose.ui.geometry.Offset(200f, 200f),
        radius = 800f
    )
    
    val secondaryGradient = Brush.radialGradient(
         colors = listOf(
            Color(0xFF6D28D9).copy(alpha = 0.2f), // Purple
            Color.Transparent
        ),
        center = androidx.compose.ui.geometry.Offset(1000f, 600f),
        radius = 600f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Almost black background
            .background(backgroundBrush)
            .background(secondaryGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp) // Smaller logo
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF00A2FF)) // Fallback blue
            ) {
                // Glow effect removed for TV performance/simplicity, focus on Image
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.coulterpeterson.floatnative.R.drawable.floatplane_logo),
                    contentDescription = "FloatNative Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "FloatNative",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Card
            Surface(
                modifier = Modifier.width(320.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.NonInteractiveSurfaceDefaults.colors(
                    containerColor = Color(0xFF1C1C1C) // Explicit Dark Grey Card
                ),
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val s = state) {
                        is TvLoginState.Loading -> {
                             CircularProgressIndicator(
                                 modifier = Modifier.padding(16.dp), 
                                 color = Color.White
                             )
                             Text("Connecting...", color = Color.White)
                        }
                        is TvLoginState.Content -> {
                            Text(
                                text = "Log in with Floatplane",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scan the QR code",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // QR Code with White Border
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(4.dp) // White padding around QR
                            ) {
                                var qrImage by remember { mutableStateOf<ImageBitmap?>(null) }
                                LaunchedEffect(s.verificationUriComplete) {
                                    qrImage = QrCodeUtils.generateQrCode(s.verificationUriComplete, 384)
                                }
                                
                                if (qrImage != null) {
                                    Image(
                                        bitmap = qrImage!!,
                                        contentDescription = "Scan QR Code",
                                        modifier = Modifier.size(130.dp)
                                    )
                                } else {
                                    Box(modifier = Modifier.size(130.dp)) // Placeholder space
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // User Code
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                colors = androidx.tv.material3.NonInteractiveSurfaceDefaults.colors(
                                    containerColor = Color(0xFF333333) // Darker pill
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = s.userCode,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 4.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.White
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                             
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), 
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        }
                        is TvLoginState.Error -> {
                            Text("Error", color = MaterialTheme.colorScheme.error)
                            Text(s.message, color = Color.White)
                        }
                        is TvLoginState.Success -> {
                            LaunchedEffect(Unit) {
                                onLoginSuccess()
                            }
                            Text("Success!", color = Color.Green)
                        }
                    }
                }
            }
        }
    }
}
