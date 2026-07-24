package com.devflow.common.exception;

import com.devflow.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 — 支持 int code（兼容旧代码）和 ErrorCode 枚举
 *
 * 推荐: new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + id)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    // ===== 旧 API — 兼容 =====

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    // ===== 新 API — 推荐使用 ErrorCode 枚举 =====

    /**
     * 使用 ErrorCode 并附带自定义消息详情。
     * 示例: throw new BusinessException(ErrorCode.NOT_FOUND, "Project not found: " + id);
     */
    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.code = errorCode.getHttpCode();
    }

    /**
     * 仅指定 ErrorCode，使用默认消息。
     * 示例: throw new BusinessException(ErrorCode.INVALID_STATE);
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getHttpCode();
    }
}
