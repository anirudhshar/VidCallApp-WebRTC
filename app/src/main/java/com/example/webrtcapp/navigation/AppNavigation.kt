package com.example.webrtcapp.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.webrtcapp.screens.HomeScreen
import com.example.webrtcapp.screens.PermissionScreen
import com.example.webrtcapp.screens.VideoCallScreen
import timber.log.Timber

@Composable
fun WebRTCAppNavigation() {

    CompositionLocalProvider(
        LocalNavController provides rememberNavController()
    ) {

        SetUpNavigation()

    }


}

@Composable
fun SetUpNavigation() {
    val navController = LocalNavController.current

    val context = LocalContext.current

    val isCameraPermissionGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    NavHost(
        navController = navController, startDestination =
        if (isCameraPermissionGranted) "home" else "permission"
    ) {

        composable("home") {
            HomeScreen(navigateToCallScreen = {roomId ->
                navController.navigate("videoCallScreen/${roomId}")
            })

        }
        composable("permission") {
            PermissionScreen(navigateToHomeScreen = {
                navController.navigate("home") {

                    //this is done to close the app when we navigate back from  home screen
                    popUpTo("0") {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            })

        }
        composable("videoCallScreen/{roomId}") {backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            if(roomId.isNullOrEmpty()){
                Timber.e("Room ID is not available")
                return@composable
            }
            VideoCallScreen(roomId, onNavigateBack = {
                navController.popBackStack()
            })

        }


    }

}

val LocalNavController =
    compositionLocalOf<NavHostController> { error("No LocalNavController provided") }