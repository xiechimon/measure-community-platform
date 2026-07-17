package com.measure.community.info.support;

import com.measure.community.common.crypto.SensitiveCrypto;

/**
 * 敏感字段盲索引(HMAC)。见《详细功能设计说明书》§5:
 * 加密字段落库为 AES-256 密文,另存一列 HMAC-SHA256 盲索引用于唯一/等值精确匹配。
 * 实际算法与密钥集中在 {@link SensitiveCrypto}。
 */
public final class HmacUtil {
    private HmacUtil() {}
    public static String blindIndex(String plain) {
        return SensitiveCrypto.blindIndex(plain);
    }
}
