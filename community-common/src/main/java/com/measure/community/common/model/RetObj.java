package com.measure.community.common.model;

import com.measure.community.common.constant.CommonConstant;
import com.measure.community.common.enums.ErrorCode;
import com.measure.community.common.enums.SystemStatus;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 全局统一响应对象(依据详细设计说明书 §7.1):{@code {code, message, data, traceId}}。
 * <p>{@code code} 为分段业务码(见 {@link SystemStatus});{@code traceId} 由构造时从 MDC 全链路
 * 追踪 ID 自动回填,便于报障时按 traceId 检索日志。
 */
@Data
public class RetObj<T> {

    private Integer code;
    private String message;
    private T data;
    private String traceId;

    public RetObj(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(CommonConstant.TRACE_ID_HEADER);
    }

    public RetObj(ErrorCode status) {
        this(status.getCode(), status.getErrorMessage(), null);
    }

    public RetObj(ErrorCode status, T data) {
        this(status.getCode(), status.getErrorMessage(), data);
    }

    public static <T> RetObj<T> success() {
        return new RetObj<>(SystemStatus.SUCCESS);
    }

    public static <T> RetObj<T> success(T data) {
        return new RetObj<>(SystemStatus.SUCCESS, data);
    }

    public static <T> RetObj<T> error(ErrorCode status) {
        return new RetObj<>(status);
    }

    public static <T> RetObj<T> error(ErrorCode status, String message) {
        return new RetObj<>(status.getCode(), message, null);
    }

    public static <T> RetObj<T> error(String message) {
        return new RetObj<>(SystemStatus.INTERNAL_ERROR.getCode(), message, null);
    }
}
