package com.measure.community.integration;

import com.measure.community.common.crypto.AesTypeHandler;
import com.measure.community.common.crypto.SensitiveCrypto;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitivePersistenceIT extends MySqlIntegrationSupport {
    @Test
    void idCardIsEncryptedAtRestAndRoundTripsThroughTypeHandler() throws Exception {
        flyway.clean();
        flyway.migrate();
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        SensitiveCrypto.configure(key, key);
        AesTypeHandler handler = new AesTypeHandler();
        String plain = "330106199001011234";
        String blind = SensitiveCrypto.blindIndex(plain);

        assertEquals(blind, SensitiveCrypto.blindIndex(plain));
        assertNotEquals(blind, SensitiveCrypto.blindIndex("330106199001019999"));
        assertTrue(blind.matches("[0-9a-f]{64}"));
        assertNotEquals(SensitiveCrypto.encrypt(plain), SensitiveCrypto.encrypt(plain));

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO t_population(id,type,name,id_card,id_card_hmac,version) "
                            + "VALUES(1,'户籍','张三',?,?,1)")) {
                handler.setNonNullParameter(statement, 1, plain, JdbcType.VARCHAR);
                statement.setString(2, blind);
                assertEquals(1, statement.executeUpdate());
            }

            try (ResultSet raw = connection.createStatement().executeQuery(
                    "SELECT id_card,id_card_hmac FROM t_population WHERE id=1")) {
                assertTrue(raw.next());
                String ciphertext = raw.getString("id_card");
                assertFalse(ciphertext.isBlank());
                assertNotEquals(plain, ciphertext);
                assertEquals(blind, raw.getString("id_card_hmac"));
                assertEquals(plain, handler.getNullableResult(raw, "id_card"));
            }
        }
    }
}
