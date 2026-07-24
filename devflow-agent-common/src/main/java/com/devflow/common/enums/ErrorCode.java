package com.devflow.common.enums;

/**
 * 统一业务错误码
 *
 * 约定: 1xxx=参数校验 2xxx=认证授权 3xxx=业务逻辑 4xxx=外部依赖 5xxx=系统错误
 */
public enum ErrorCode {

    // ---- 1xxx: 客户端错误 / 参数校验 ----
    BAD_REQUEST(400, "Bad request"),
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_JSON(400, "Malformed JSON body"),
    INVALID_APPROVAL_ACTION(400, "Invalid approval action, must be APPROVED or REJECTED"),

    // NOTE: 401 is a Spring Security status; business exceptions don't fire for unauthenticated requests.
    // UNAUTHORIZED(401, "Authentication required"),

    // ---- 2xxx: 权限错误 ----
    FORBIDDEN(403, "Access denied"),

    // ---- 3xxx: 资源 / 业务逻辑 ----
    NOT_FOUND(404, "Resource not found"),
    DUPLICATE_RESOURCE(409, "Resource already exists"),
    INVALID_STATE(422, "Invalid state for this operation"),

    // ---- 5xxx: 系统 / 外部依赖 ----
    INTERNAL_ERROR(500, "Internal server error"),
    AI_AGENT_FAILED(502, "AI agent execution failed"),
    GITHUB_API_FAILED(503, "GitHub API call failed");

    private final int httpCode;
    private final String defaultMessage;

    ErrorCode(int httpCode, String defaultMessage) {
        this.httpCode = httpCode;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpCode() { return httpCode; }
    public String getDefaultMessage() { return defaultMessage; }
}
