package com.mtd.data.service

import com.mtd.data.dto.NotificationOkDto
import com.mtd.data.dto.NotificationStatusResponseDto
import com.mtd.data.dto.RegisterDeviceRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Phase 4 — Push notifications (`/api/notifications`). Device identity for register/unregister comes
 * from the bearer JWT attached by the AuthInterceptor; `/status` is unauthenticated.
 */
interface NotificationApiService {

    @POST("api/notifications/devices")
    suspend fun registerDevice(
        @Body body: RegisterDeviceRequestDto
    ): Response<NotificationOkDto>

    @DELETE("api/notifications/devices/{token}")
    suspend fun unregisterDevice(
        @Path("token") token: String
    ): Response<NotificationOkDto>

    @GET("api/notifications/status")
    suspend fun getStatus(): Response<NotificationStatusResponseDto>
}
