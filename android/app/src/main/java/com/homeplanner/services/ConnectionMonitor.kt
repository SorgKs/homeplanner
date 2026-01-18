package com.homeplanner.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Монитор состояния сетевого соединения.
 * Предоставляет LiveData для отслеживания доступности сети.
 */
class ConnectionMonitor(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isNetworkAvailable = MutableLiveData<Boolean>()
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            _isNetworkAvailable.postValue(true)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            _isNetworkAvailable.postValue(false)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Network capabilities changed: hasInternet=$hasInternet, hasValidated=$hasValidated")
            _isNetworkAvailable.postValue(hasInternet && hasValidated)
        }
    }

    private var monitoringJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    init {
        startMonitoring()
    }

    /**
     * Запускает мониторинг сети.
     */
    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }

        // Инициальная проверка
        checkInitialNetworkState()

        // Периодическая проверка для старых версий Android
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            startPeriodicCheck()
        }
    }

    /**
     * Останавливает мониторинг сети.
     */
    fun stopMonitoring() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
            monitoringJob?.cancel()
            monitoringJob = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping network monitoring", e)
        }
    }

    /**
     * Проверяет текущее состояние сети.
     */
    fun isNetworkAvailableNow(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnectedOrConnecting == true
        }
    }

    private fun checkInitialNetworkState() {
        val isAvailable = isNetworkAvailableNow()
        _isNetworkAvailable.postValue(isAvailable)
        Log.d(TAG, "Initial network state: $isAvailable")
    }

    private fun startPeriodicCheck() {
        monitoringJob = scope.launch {
            while (true) {
                delay(5000L) // Проверка каждые 5 секунд
                val currentState = isNetworkAvailableNow()
                val previousState = _isNetworkAvailable.value ?: false
                if (currentState != previousState) {
                    _isNetworkAvailable.postValue(currentState)
                    Log.d(TAG, "Network state changed: $currentState")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionMonitor"
    }
}