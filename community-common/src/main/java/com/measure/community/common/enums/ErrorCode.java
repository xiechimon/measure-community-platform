package com.measure.community.common.enums;

/**
 * 错误码契约。依据详细设计说明书 §7.1:响应体 code 采用分段业务码
 * (1xxxx 鉴权 / 2xxxx 业务 / 5xxxx 系统),HTTP status 另置(语义化)。
 * 各业务模块可自定义枚举实现本接口,扩展自己的 2xxxx 业务码。
 */
public interface ErrorCode {

    /** 分段业务码,写入响应体 code */
    Integer getCode();

    /** HTTP 状态码,设置到 HTTP 响应行 */
    Integer getHttpStatus();

    /** 默认错误消息 */
    String getErrorMessage();
}
