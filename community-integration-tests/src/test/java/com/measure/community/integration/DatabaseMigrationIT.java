package com.measure.community.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationIT extends MySqlIntegrationSupport {
    @Test
    void emptyDatabaseMigratesOnceAndAdminCanAuthenticate() throws Exception {
        flyway.clean();

        assertEquals(6, flyway.migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ResultSet tables = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema='measure_community' AND table_name IN "
                            + "('t_population','t_population_his','sys_user','sys_role')");
            assertTrue(tables.next());
            assertEquals(4, tables.getInt(1));

            ResultSet user = connection.createStatement().executeQuery(
                    "SELECT password FROM sys_user WHERE username='admin'");
            assertTrue(user.next());
            assertTrue(new BCryptPasswordEncoder().matches("123456", user.getString(1)));

            // V6 组织管理权限种子(system:org:*)已落库
            ResultSet orgPerms = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM sys_permission WHERE code LIKE 'system:org:%'");
            assertTrue(orgPerms.next());
            assertEquals(5, orgPerms.getInt(1));
        }

        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
