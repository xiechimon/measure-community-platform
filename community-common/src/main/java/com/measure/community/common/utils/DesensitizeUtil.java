package com.measure.community.common.utils;

/**
 * 展示脱敏规则集中地(§5.1.5)。各 service 映射 DTO 时统一调用,不要各自手写。
 * <p>注:按字段级权限在序列化层自动脱敏依赖 §6 权限体系,属后续阶段;本类只提供算法。
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /** 证件号:保留前 6 后 4,中间打码;长度不足 10 位整体打码。 */
    public static String idCard(String v) {
        if (v == null) {
            return null;
        }
        if (v.length() <= 10) {
            return "****";
        }
        return v.substring(0, 6) + "*".repeat(v.length() - 10) + v.substring(v.length() - 4);
    }

    /** 手机号:保留前 3 后 4(仅对 11 位手机号)。 */
    public static String phone(String v) {
        if (v == null) {
            return null;
        }
        if (v.length() < 7) {
            return "****";
        }
        return v.substring(0, 3) + "*".repeat(v.length() - 7) + v.substring(v.length() - 4);
    }

    /** 银行卡:仅保留后 4。 */
    public static String bankCard(String v) {
        if (v == null) {
            return null;
        }
        if (v.length() <= 4) {
            return "****";
        }
        return "*".repeat(v.length() - 4) + v.substring(v.length() - 4);
    }
}
