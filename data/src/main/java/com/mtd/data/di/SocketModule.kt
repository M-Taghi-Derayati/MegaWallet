package com.mtd.data.di

import com.mtd.core.socket.IWebSocketClient
import com.mtd.core.socket.RawWebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object SocketModule {
    @Provides
    fun provideRawWebSocketClient(
        @Named("WebSocketClient") okHttpClient: OkHttpClient
    ): IWebSocketClient {
        return RawWebSocketClient(okHttpClient)
    }

    // --- بخش جدید و مهم ---
    // این به Hilt یاد می‌دهد که چطور یک FACTORY برای IWebSocketClient بسازد.
    @Provides
    fun provideWebSocketClientFactory(
        // ما از Provider<IWebSocketClient> به جای خود IWebSocketClient استفاده می‌کنیم
        clientProvider: javax.inject.Provider<IWebSocketClient>
    ): () -> IWebSocketClient {
        // ما یک لامبدا برمی‌گردانیم که هر بار فراخوانی شود،
        // از Hilt یک نمونه جدید درخواست می‌کند.
        return { clientProvider.get() }
    }
}