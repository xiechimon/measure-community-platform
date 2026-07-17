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

    /**
     * 业务响应码。
     */
    private Integer code;

    /**
     * 响应提示信息。
     */
    private String message;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 链路追踪 ID，用于关联请求日志。
     */
    private String traceId;

    /**
     * 使用响应码、提示信息和数据创建响应对象。
     *
     * @param code    业务响应码
     * @param message 响应提示信息
     * @param data    响应数据
     */
    public RetObj(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(CommonConstant.TRACE_ID_HEADER);
    }

    /**
     * 根据错误码创建不含数据的响应对象。
     *
     * @param status 错误码
     */
    public RetObj(ErrorCode status) {
        this(status.getCode(), status.getErrorMessage(), null);
    }

    /**
     * 根据错误码和数据创建响应对象。
     *
     * @param status 错误码
     * @param data   响应数据
     */
    public RetObj(ErrorCode status, T data) {
        this(status.getCode(), status.getErrorMessage(), data);
    }

    /**
     * 创建成功且不含数据的响应对象。
     *
     * @param <T> 响应数据类型
     * @return 成功响应对象
     */
    public static <T> RetObj<T> success() {
        return new RetObj<>(SystemStatus.SUCCESS);
    }

    /**
     * 创建携带数据的成功响应对象。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应对象
     */
    public static <T> RetObj<T> success(T data) {
        return new RetObj<>(SystemStatus.SUCCESS, data);
    }

    /**
     * 根据错误码创建错误响应对象。
     *
     * @param status 错误码
     * @param <T>    响应数据类型
     * @return 错误响应对象
     */
    public static <T> RetObj<T> error(ErrorCode status) {
        return new RetObj<>(status);
    }

    /**
     * 根据错误码和自定义提示信息创建错误响应对象。
     *
     * @param status  错误码
     * @param message 自定义错误提示信息
     * @param <T>     响应数据类型
     * @return 错误响应对象
     */
    public static <T> RetObj<T> error(ErrorCode status, String message) {
        return new RetObj<>(status.getCode(), message, null);
    }

    /**
     * 使用内部错误码创建错误响应对象。
     *
     * @param message 错误提示信息
     * @param <T>     响应数据类型
     * @return 错误响应对象
     */
    public static <T> RetObj<T> error(String message) {
        return new RetObj<>(SystemStatus.INTERNAL_ERROR.getCode(), message, null);
    }
}
