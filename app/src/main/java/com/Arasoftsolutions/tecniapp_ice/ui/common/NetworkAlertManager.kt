package com.Arasoftsolutions.tecniapp_ice.ui.common

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Arasoftsolutions.tecniapp_ice.R

class NetworkAlertManager(private val application: Application) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var currentActivity: Activity? = null
    private var currentAlertView: View? = null
    private var hideRunnable: Runnable? = null
    private var isConnected: Boolean = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            updateConnectionState(true)
        }

        override fun onLost(network: android.net.Network) {
            updateConnectionState(false)
        }
    }

    fun start() {
        registerActivityCallbacks()
        registerNetworkCallback()
        updateConnectionFromSystem()
    }

    private fun registerActivityCallbacks() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    currentActivity = activity
                    if (!isConnected) {
                        showOfflineAlert()
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    if (currentActivity == activity) {
                        currentActivity = null
                    }
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivity == activity) {
                        currentActivity = null
                    }
                    if (currentAlertView?.context == activity) {
                        currentAlertView = null
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            }
        )
    }

    private fun registerNetworkCallback() {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun updateConnectionFromSystem() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        updateConnectionState(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    private fun updateConnectionState(connected: Boolean) {
        isConnected = connected
        mainHandler.post {
            if (connected) {
                hideOfflineAlert()
            } else {
                showOfflineAlert()
            }
        }
    }

    private fun showOfflineAlert() {
        val activity = currentActivity ?: return
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        if (currentAlertView?.parent == parent) return
        currentAlertView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            currentAlertView = null
        }

        val alertView = LayoutInflater.from(activity).inflate(R.layout.layout_top_alert, parent, false)
        val textView = alertView.findViewById<TextView>(R.id.tvMessage)
        val iconView = alertView.findViewById<ImageView>(R.id.iconAlert)

        textView.text = activity.getString(R.string.offline_alert_message)
        alertView.setBackgroundColor(ContextCompat.getColor(activity, R.color.red))
        iconView.setColorFilter(ContextCompat.getColor(activity, android.R.color.white))

        parent.addView(alertView)
        currentAlertView = alertView

        alertView.translationY = -200f
        alertView.alpha = 0f
        alertView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(350)
            .start()

        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = Runnable { hideOfflineAlert() }
        mainHandler.postDelayed(hideRunnable!!, 3000)
    }

    private fun hideOfflineAlert() {
        val alert = currentAlertView ?: return
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
        alert.animate()
            .translationY(-200f)
            .alpha(0f)
            .setDuration(350)
            .withEndAction {
                (alert.parent as? ViewGroup)?.removeView(alert)
                if (currentAlertView == alert) {
                    currentAlertView = null
                }
            }
            .start()
    }
}
