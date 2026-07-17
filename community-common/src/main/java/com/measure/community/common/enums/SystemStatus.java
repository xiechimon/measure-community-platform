package com.measure.community.common.enums;

import lombok.Getter;

@Getter
public enum SystemStatus {

    SUCCESS(200, "请求成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或token失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),
    ;

    private final Integer code;
    private final String errorMessage;

    SystemStatus(Integer code, String errorMessage) {
        this.code = code;
        this.errorMessage = errorMessage;
    }
}
