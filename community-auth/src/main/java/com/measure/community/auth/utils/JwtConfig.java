package com.measure.community.auth.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时把配置中的 JWT 密钥/过期注入 {@link JwtTokenUtils}。
 * 配置项:{@code jwt.secret}、{@code jwt.expire-seconds}(生产由 Nacos 注入)。
 */
@Slf4j
@Component
public class JwtConfig {

    public JwtConfig(@Value("${jwt.secret:}") String secret,
                     @Value("${jwt.expire-seconds:7200}") long expireSeconds) {
        JwtTokenUtils.configure(secret, expireSeconds);
        if (!StringUtils.hasText(secret)) {
            log.warn("JWT 密钥未配置(jwt.secret),使用开发默认密钥;生产环境请务必经 Nacos 注入!");
        }
    }
}
