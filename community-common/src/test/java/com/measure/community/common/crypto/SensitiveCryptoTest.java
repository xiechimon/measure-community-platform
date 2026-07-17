package com.measure.community.common.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveCryptoTest {

    @Test
    void encryptDecrypt_roundTrips() {
        String plain = "330106199001011234";
        String cipher = SensitiveCrypto.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertEquals(plain, SensitiveCrypto.decrypt(cipher));
    }

    @Test
    void encrypt_sameInput_producesDifferentCipher_butSamePlain() {
        String plain = "13800001111";
        String c1 = SensitiveCrypto.encrypt(plain);
        String c2 = SensitiveCrypto.encrypt(plain);
        assertNotEquals(c1, c2, "随机 IV 应使两次密文不同");
        assertEquals(plain, SensitiveCrypto.decrypt(c1));
        assertEquals(plain, SensitiveCrypto.decrypt(c2));
    }

    @Test
    void blindIndex_isDeterministic_andDistinct() {
        String a = SensitiveCrypto.blindIndex("330106199001011234");
        String b = SensitiveCrypto.blindIndex("330106199001011234");
        String c = SensitiveCrypto.blindIndex("330106199001019999");
        assertEquals(a, b, "同明文盲索引须一致(支撑唯一约束/等值查询)");
        assertNotEquals(a, c);
        assertTrue(a.matches("[0-9a-f]{64}"), "HMAC-SHA256 十六进制应为 64 字符");
    }

    @Test
    void nulls_passThrough() {
        assertNull(SensitiveCrypto.encrypt(null));
        assertNull(SensitiveCrypto.decrypt(null));
        assertNull(SensitiveCrypto.blindIndex(null));
    }
}
