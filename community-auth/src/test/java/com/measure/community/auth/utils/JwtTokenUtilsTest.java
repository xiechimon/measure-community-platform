package com.measure.community.auth.utils;

import com.auth0.jwt.interfaces.Claim;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenUtilsTest {

    @Test
    void createAndVerify_roundTrips_withExpiry() {
        JwtTokenUtils.configure("unit-test-secret", 3600);
        String token = JwtTokenUtils.createToken(42L);
        assertNotNull(token);

        Map<String, Claim> claims = JwtTokenUtils.verifyToken(token);
        assertNotNull(claims);
        assertEquals("42", claims.get("userId").asString());
        assertNotNull(claims.get("exp"), "token 应含过期时间");
        assertEquals(42L, JwtTokenUtils.getUserId(token));
    }

    @Test
    void verify_wrongSecret_returnsNull() {
        JwtTokenUtils.configure("secret-A", 3600);
        String token = JwtTokenUtils.createToken(1L);
        JwtTokenUtils.configure("secret-B", 3600);
        assertNull(JwtTokenUtils.verifyToken(token), "换密钥后旧 token 应校验失败");
        // 复原,避免影响其它用例
        JwtTokenUtils.configure("secret-A", 3600);
        assertTrue(JwtTokenUtils.getUserId(token) == 1L);
    }
}
