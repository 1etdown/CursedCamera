package com.example.cursedcamera.utils

import android.Manifest
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

object PermissionsUtils {
    private val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    fun hasCameraPermissions(context: Context): Boolean {
        return CAMERA_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermissions(fragment: Fragment) {
        fragment.requestPermissions(CAMERA_PERMISSIONS, 0)
    }
}
