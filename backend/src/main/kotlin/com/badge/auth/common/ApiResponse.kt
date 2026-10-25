package com.badge.auth.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiResponse<T>(
    val code: Int = 200,
    val message: String = "success",
    val data: T? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun <T> success(data: T? = null, message: String = "success"): ApiResponse<T> {
            return ApiResponse(200, message, data)
        }

        fun <T> error(code: Int = 400, message: String): ApiResponse<T> {
            return ApiResponse(code, message, null)
        }
    }
}

class BusinessException(
    val code: Int = 400,
    override val message: String
) : RuntimeException(message)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(if (ex.code in 400..599) ex.code else HttpStatus.BAD_REQUEST.value())
            .body(ApiResponse.error(ex.code, ex.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errorMsg = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, errorMsg))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, ex.message ?: "Internal Server Error"))
    }
}
