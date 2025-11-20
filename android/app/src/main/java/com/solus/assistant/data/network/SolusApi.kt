package com.solus.assistant.data.network

import com.solus.assistant.data.model.ChatRequest
import com.solus.assistant.data.model.ChatResponse
import com.solus.assistant.data.model.HealthResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit API interface for Solus server
 */
interface SolusApi {
    /**
     * Health check endpoint
     */
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    /**
     * Chat endpoint - sends user message and receives AI response
     */
    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    /**
     * Clear memory endpoint (placeholder)
     */
    @POST("memory/clear")
    suspend fun clearMemory(@Body userId: Map<String, String>): Response<Map<String, String>>
}
