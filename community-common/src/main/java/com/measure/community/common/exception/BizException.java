package com.measure.community.common.exception;

import com.measure.community.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。service 层遇业务错误抛出,由 GlobalExceptionHandler 统一转 RetObj + HTTP status。
 * 携带 {@link ErrorCode}(可为通用 {@code SystemStatus} 或模块自定义业务码)。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getErrorMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
