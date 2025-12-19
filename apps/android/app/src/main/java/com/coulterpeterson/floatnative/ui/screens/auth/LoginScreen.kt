package com.coulterpeterson.floatnative.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coulterpeterson.floatnative.R
import com.coulterpeterson.floatnative.viewmodels.LoginState
import com.coulterpeterson.floatnative.viewmodels.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Use MaterialTheme which respects the app's theme setting (light/dark/system)
    // Check if background is dark by comparing RGB values
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (backgroundColor.red + backgroundColor.green + backgroundColor.blue) / 3f < 0.5f
    
    // Color scheme based on theme
    val backgroundGradient = if (isDarkTheme) {
        listOf(
            Color(0xFF1a2332), // Dark blue
            Color(0xFF2a2a3e), // Mid tone
            Color(0xFF3d2847)  // Dark purple
        )
    } else {
        listOf(
            Color(0xFFE8F4F8), // Light blue
            Color(0xFFF0F0F5), // Light gray
            Color(0xFFF5E8F0)  // Light purple
        )
    }
    
    val titleColor = if (isDarkTheme) Color.White else Color(0xFF1a1a1a)
    val subtitleColor = if (isDarkTheme) Color(0xFFB0B0B0) else Color(0xFF666666)
    val cardColor = if (isDarkTheme) Color(0xCC2a2a2a) else Color(0xCCFFFFFF)

    // Error Handling & Success
    LaunchedEffect(state) {
        if (state is LoginState.Error) {
            snackbarHostState.showSnackbar((state as LoginState.Error).message)
        } else if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = backgroundGradient)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section with logo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo with glow effect
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Glow effect layer
                    Image(
                        painter = painterResource(id = R.drawable.floatplane_logo),
                        contentDescription = "FloatNative Logo",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF3B9FE8)) // Blue background matching logo
                            .blur(40.dp)
                    )
                    // Actual logo
                    Image(
                        painter = painterResource(id = R.drawable.floatplane_logo),
                        contentDescription = "FloatNative Logo",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF3B9FE8)) // Blue background matching logo
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "FloatNative",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Sign in to continue",
                    fontSize = 14.sp,
                    color = subtitleColor
                )
            }
            
            // Bottom section with button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to FloatNative",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val context = androidx.compose.ui.platform.LocalContext.current
                
                // Login button
                Button(
                    onClick = { viewModel.startAuthFlow(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0099ff),
                        contentColor = Color.White
                    ),
                    enabled = state !is LoginState.Loading
                ) {
                    if (state is LoginState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log in with Floatplane",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Error message below button
                if (state is LoginState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (state as LoginState.Error).message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}
