package com.measure.community.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * 启动时把配置中的敏感字段密钥注入 {@link SensitiveCrypto}。
 * 配置项(Base64):{@code sensitive.aes-key}(AES-256,解码后 32 字节)、{@code sensitive.hmac-key}。
 * 生产由 Nacos 注入;未配置则沿用开发默认密钥并告警。
 */
@Slf4j
@Component
public class SensitiveCryptoInitializer {

    public SensitiveCryptoInitializer(
            @Value("${sensitive.aes-key:}") String aesKeyB64,
            @Value("${sensitive.hmac-key:}") String hmacKeyB64) {
        byte[] aes = StringUtils.hasText(aesKeyB64) ? Base64.getDecoder().decode(aesKeyB64) : null;
        byte[] hmac = StringUtils.hasText(hmacKeyB64) ? Base64.getDecoder().decode(hmacKeyB64) : null;
        SensitiveCrypto.configure(aes, hmac);
        if (aes == null || hmac == null) {
            log.warn("敏感字段密钥未完整配置(sensitive.aes-key/hmac-key),使用开发默认密钥;生产环境请务必经 Nacos 注入!");
        }
    }
}
