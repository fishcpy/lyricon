/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.proify.lyricon.central.CentralReceiver.RECOVERY_WINDOW_MS
import io.github.proify.lyricon.central.provider.ProviderManager
import io.github.proify.lyricon.central.provider.RemoteProvider
import io.github.proify.lyricon.central.subscriber.RemoteSubscriber
import io.github.proify.lyricon.central.subscriber.SubscriberManager
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo
import io.github.proify.lyricon.subscriber.ISubscriberBinder
import io.github.proify.lyricon.subscriber.SubscriberInfo
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lyricon 中央接收器。
 *
 * 负责跨进程 Provider 与 Subscriber 的注册分发。
 * 包含恢复期机制：启动前 [RECOVERY_WINDOW_MS] 毫秒内暂存 Provider 请求，
 * 优先保证 Subscriber 注册完成，随后统一处理积压队列。
 */
internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"
    private const val RECOVERY_WINDOW_MS = 3000L

    private val pendingProviders = CopyOnWriteArraySet<Intent>()

    @Volatile
    private var isRecoveryPhase = true

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 冲刷积压的 Provider 队列。
     */
    private val flushTask = Runnable {
        if (!isRecoveryPhase) return@Runnable
        isRecoveryPhase = false

        if (pendingProviders.isNotEmpty()) {
            Log.d(TAG, "Recovery phase ended. Flushing ${pendingProviders.size} providers.")
            pendingProviders.forEach { intent ->
                registerProvider(intent)
            }
            pendingProviders.clear()
        }
    }

    init {
        mainHandler.postDelayed(flushTask, RECOVERY_WINDOW_MS)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received intent: $action")

        when (action) {
            Constants.ACTION_REGISTER_SUBSCRIBER -> {
                registerSubscriber(intent)
            }
            Constants.ACTION_REGISTER_PROVIDER -> {
                if (isRecoveryPhase) {
                    pendingProviders.add(intent)
                    Log.d(TAG, "Provider registration pended during recovery.")
                } else {
                    registerProvider(intent)
                }
            }
        }
    }

    /**
     * 从 Intent Bundle 中提取并转换指定类型的 AIDL Binder。
     */
    private inline fun <reified T : Any> getBinder(intent: Intent): T? = runCatching {
        val binder = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
            ?.getBinder(Constants.EXTRA_BINDER) ?: return null

        when (T::class) {
            IProviderBinder::class -> IProviderBinder.Stub.asInterface(binder) as? T
            ISubscriberBinder::class -> ISubscriberBinder.Stub.asInterface(binder) as? T
            else -> null
        }
    }.onFailure {
        Log.e(TAG, "Failed to retrieve binder", it)
    }.getOrNull()

    /**
     * 处理内容提供者注册。
     */
    private fun registerProvider(intent: Intent) {
        val binder = getBinder<IProviderBinder>(intent) ?: return
        var provider: RemoteProvider? = null

        try {
            val providerInfo = binder.providerInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(ProviderInfo.serializer(), it) }

            if (providerInfo?.providerPackageName.isNullOrBlank() ||
                providerInfo.playerPackageName.isBlank()
            ) {
                Log.w(TAG, "Invalid provider info rejected.")
                return
            }

            val registered = ProviderManager.getProvider(providerInfo)
            if (registered != null) {
                provider = registered
                Log.d(TAG, "Reusing existing provider: ${providerInfo.playerPackageName}")
            } else {
                provider = RemoteProvider(binder, providerInfo)
                ProviderManager.register(provider)
                Log.d(TAG, "New provider registered: ${providerInfo.playerPackageName}")
            }

            binder.onRegistrationCallback(provider.service)

        } catch (e: Exception) {
            Log.e(TAG, "Provider registration failed", e)
            provider?.let { ProviderManager.unregister(it) }
        }
    }

    /**
     * 处理订阅者注册。
     */
    private fun registerSubscriber(intent: Intent) {
        val binder = getBinder<ISubscriberBinder>(intent) ?: return
        var subscriber: RemoteSubscriber? = null

        try {
            val subscriberInfo = binder.subscriberInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(SubscriberInfo.serializer(), it) }

            if (subscriberInfo?.packageName.isNullOrBlank() ||
                subscriberInfo.processName.isBlank()
            ) {
                Log.w(TAG, "Invalid subscriber info rejected.")
                return
            }

            val registered = SubscriberManager.getSubscriber(subscriberInfo)
            if (registered != null) {
                subscriber = registered
                Log.d(TAG, "Reusing existing subscriber: ${subscriberInfo.packageName}")
            } else {
                subscriber = RemoteSubscriber(binder, subscriberInfo)
                SubscriberManager.register(subscriber)
                Log.d(TAG, "New subscriber registered: ${subscriberInfo.packageName}")
            }

            binder.onRegistrationCallback(subscriber.service)

        } catch (e: Exception) {
            Log.e(TAG, "Subscriber registration failed", e)
            subscriber?.let { SubscriberManager.unregister(it) }
        }
    }
}