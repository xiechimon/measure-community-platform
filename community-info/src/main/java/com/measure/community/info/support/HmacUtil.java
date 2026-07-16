package com.measure.community.info.support;

/**
 * 敏感字段盲索引(HMAC)接线占位。见《详细功能设计说明书》§5:
 * 加密字段落库为 AES-256 密文,另存一列 HMAC 盲索引用于唯一/等值精确匹配。
 */
public final class HmacUtil {
    private HmacUtil() {}
    /** TODO 接入真实 HMAC-SHA256 + 密钥,当前为直通占位(仅供骨架跑通) */
    public static String blindIndex(String plain) {
        return plain == null ? null : plain;
    }
}
