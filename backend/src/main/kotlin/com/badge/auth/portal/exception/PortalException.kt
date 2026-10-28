package com.badge.auth.portal.exception

import com.badge.auth.common.BusinessException

/** 通过现有统一异常处理器返回明确的门户业务错误。 */
object PortalErrors {
    @Throws(BusinessException::class)
    fun invalid(message: String): Nothing = throw BusinessException(400, message)
    @Throws(BusinessException::class)
    fun missing(): Nothing = throw BusinessException(404, "内容不存在")
    @Throws(BusinessException::class)
    fun conflict(message: String): Nothing = throw BusinessException(409, message)
}
