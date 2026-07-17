package com.measure.community.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Path;

abstract class MySqlIntegrationSupport {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("measure_community")
            .withUsername("measure")
            .withPassword("measure-test-password");
    static Flyway flyway;

    @BeforeAll
    static void startMySql() {
        MYSQL.start();
        String root = System.getProperty("project.root");
        String location = "filesystem:" + Path.of(root, "database/mysql/migration").toAbsolutePath();
        flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations(location)
                .cleanDisabled(false)
                .load();
    }

    @AfterAll
    static void stopMySql() {
        MYSQL.stop();
    }
}
