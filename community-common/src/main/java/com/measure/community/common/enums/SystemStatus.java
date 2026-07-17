package com.measure.community.common.enums;

import lombok.Getter;

/**
 * 平台通用状态码(依据详细设计说明书 §7.1 分段:1xxxx 鉴权 / 2xxxx 业务 / 5xxxx 系统)。
 * <p>{@code code} 为写入响应体的分段业务码,{@code httpStatus} 为语义化 HTTP 状态。
 * 模块专属业务码请另建枚举实现 {@link ErrorCode},不要堆在此处。
 */
@Getter
public enum SystemStatus implements ErrorCode {

    SUCCESS(200, 200, "请求成功"),

    // ---- 1xxxx 鉴权 ----
    UNAUTHORIZED(10001, 401, "未认证或token失效"),
    FORBIDDEN(10002, 403, "无权限访问"),

    // ---- 2xxxx 业务(通用)----
    BAD_REQUEST(20001, 400, "请求参数错误"),
    NOT_FOUND(20002, 404, "资源不存在"),
    METHOD_NOT_ALLOWED(20003, 405, "请求方法不支持"),
    CONFLICT(20004, 409, "数据冲突"),

    // ---- 5xxxx 系统 ----
    INTERNAL_ERROR(50000, 500, "系统繁忙，请稍后重试"),
    TOO_MANY_REQUESTS(50001, 429, "请求过于频繁，请稍后再试"),
    ;

    /** 分段业务码(响应体 code) */
    private final Integer code;
    /** HTTP 状态码 */
    private final Integer httpStatus;
    private final String errorMessage;

    SystemStatus(Integer code, Integer httpStatus, String errorMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
    }
}
