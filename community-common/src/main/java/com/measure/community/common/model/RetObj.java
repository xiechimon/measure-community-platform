package com.measure.community.common.model;

import com.measure.community.common.enums.SystemStatus;
import lombok.Data;

/**
 * 全局统一响应对象。code 与 HTTP status 一致。
 */
@Data
public class RetObj<T> {

    private Integer code;
    private String message;
    private T data;

    public RetObj(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public RetObj(SystemStatus status) {
        this.code = status.getCode();
        this.message = status.getErrorMessage();
    }

    public RetObj(SystemStatus status, T data) {
        this.code = status.getCode();
        this.message = status.getErrorMessage();
        this.data = data;
    }

    public static <T> RetObj<T> success() {
        return new RetObj<>(SystemStatus.SUCCESS);
    }

    public static <T> RetObj<T> success(T data) {
        return new RetObj<>(SystemStatus.SUCCESS, data);
    }

    public static <T> RetObj<T> error(SystemStatus status) {
        return new RetObj<>(status);
    }

    public static <T> RetObj<T> error(SystemStatus status, String message) {
        return new RetObj<>(status.getCode(), message, null);
    }

    public static <T> RetObj<T> error(String message) {
        return new RetObj<>(SystemStatus.INTERNAL_ERROR.getCode(), message, null);
    }
}
