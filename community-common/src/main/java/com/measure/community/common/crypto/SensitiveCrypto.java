package com.measure.community.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 敏感字段加解密与盲索引(§5.1.2)。集中在 common,供各模块的 TypeHandler / 静态工具复用。
 * <ul>
 *   <li>加密:AES-256-GCM,随机 12 字节 IV 前置拼接密文,整体 Base64。</li>
 *   <li>盲索引:HMAC-SHA256,同明文确定性输出,支撑唯一约束与等值查询。</li>
 * </ul>
 * 密钥经 {@link SensitiveCryptoInitializer} 从配置注入({@code sensitive.aes-key}/{@code sensitive.hmac-key},
 * Base64);未配置时回退到派生的开发测试密钥(仅供离线/CI,生产必须由 Nacos 注入)。
 * KMS(§5.1.3)后续以"替换 configure() 的密钥来源"方式接入,不改调用方。
 */
public final class SensitiveCrypto {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String HMAC_ALG = "HmacSHA256";
    private static final SecureRandom RNG = new SecureRandom();

    /** 默认开发密钥:由固定口令派生 32 字节,保证长度合法;仅离线/CI 使用 */
    private static volatile byte[] aesKey = sha256("measure-community-dev-default-aes-key");
    private static volatile byte[] hmacKey = "measure-community-dev-default-hmac-key".getBytes(StandardCharsets.UTF_8);

    private SensitiveCrypto() {
    }

    /** 由配置注入真实密钥;传 null 表示该项保持默认。 */
    public static void configure(byte[] aes, byte[] hmac) {
        if (aes != null && aes.length > 0) {
            if (aes.length != 32) {
                throw new IllegalArgumentException("AES-256 密钥必须为 32 字节,实际 " + aes.length);
            }
            aesKey = aes;
        }
        if (hmac != null && hmac.length > 0) {
            hmacKey = hmac;
        }
    }

    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, AES), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段 AES 加密失败", e);
        }
    }

    public static String decrypt(String cipherB64) {
        if (cipherB64 == null) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(cipherB64);
            byte[] iv = Arrays.copyOfRange(in, 0, GCM_IV_LEN);
            byte[] ct = Arrays.copyOfRange(in, GCM_IV_LEN, in.length);
            Cipher c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, AES), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段 AES 解密失败", e);
        }
    }

    /** HMAC-SHA256 盲索引,返回十六进制串。 */
    public static String blindIndex(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALG));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("盲索引 HMAC 计算失败", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
