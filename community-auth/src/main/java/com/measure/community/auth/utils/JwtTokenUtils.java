package com.measure.community.auth.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具。密钥与过期时间经 {@link JwtConfig} 从配置注入(仿 SensitiveCrypto 静态 holder),
 * 未配置时用开发默认值。token 含 userId 与过期时间(§6:短期 Access Token)。
 */
@Slf4j
public class JwtTokenUtils {

    private static final String USER_ID = "userId";

    /** 默认开发密钥/过期(生产由 Nacos 注入 jwt.secret / jwt.expire-seconds) */
    private static volatile String tokenSecret = "measure-community-dev-jwt-secret";
    private static volatile long tokenExpireSeconds = 7200;

    private JwtTokenUtils() {
    }

    /** 由配置注入;传空/非正值则保持默认。 */
    public static void configure(String secret, long expireSeconds) {
        if (StringUtils.isNotBlank(secret)) {
            tokenSecret = secret;
        }
        if (expireSeconds > 0) {
            tokenExpireSeconds = expireSeconds;
        }
    }

    public static long getExpireSeconds() {
        return tokenExpireSeconds;
    }

    public static String createToken(Long userId) {
        try {
            Instant now = Instant.now();
            return JWT.create()
                    .withClaim("iss", "measure-community")
                    .withClaim("aud", "producer")
                    .withClaim(USER_ID, String.valueOf(userId))
                    .withIssuedAt(Date.from(now))
                    .withExpiresAt(Date.from(now.plusSeconds(tokenExpireSeconds)))
                    .sign(Algorithm.HMAC256(tokenSecret));
        } catch (Exception e) {
            log.error("生成token异常", e);
            return null;
        }
    }

    public static Map<String, Claim> verifyToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(tokenSecret)).build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaims();
        } catch (Exception e) {
            log.warn("校验Token失败: {}", e.getMessage());
            return null;
        }
    }

    public static Long getUserId(String token) {
        Map<String, Claim> claims = verifyToken(token);
        if (claims == null) {
            return null;
        }
        Claim claim = claims.get(USER_ID);
        if (claim == null || StringUtils.isEmpty(claim.asString())) {
            return null;
        }
        return Long.parseLong(claim.asString());
    }
}
