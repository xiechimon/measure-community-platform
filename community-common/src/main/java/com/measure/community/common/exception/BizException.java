package com.measure.community.common.exception;

import com.measure.community.common.enums.SystemStatus;
import lombok.Getter;

/**
 * 业务异常。service 层遇业务错误抛出,由 GlobalExceptionHandler 统一转 RetObj + HTTP status。
 */
@Getter
public class BizException extends RuntimeException {

    private final SystemStatus status;

    public BizException(SystemStatus status) {
        super(status.getErrorMessage());
        this.status = status;
    }

    public BizException(SystemStatus status, String message) {
        super(message);
        this.status = status;
    }
}
