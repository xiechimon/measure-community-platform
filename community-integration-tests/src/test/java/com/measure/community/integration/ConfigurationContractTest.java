package com.measure.community.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationContractTest {
    private static final Path PROJECT_ROOT = Path.of(
            System.getProperty("maven.multiModuleProjectDirectory", ".."))
            .toAbsolutePath().normalize();

    @Test
    void everyApplicationAndDocumentYamlParsesWithoutDuplicateKeys() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));

        assertAll(configurationFiles().stream()
                .<Executable>map(file -> () -> parseEveryDocument(yaml, file))
                .toList());
    }

    @Test
    void bootstrapProfilesAndSharedSecuritySettingsUseEnvironmentContracts() throws Exception {
        String gateway = read("community-gateway/src/main/resources/application.yml");
        String auth = read("community-auth/src/main/resources/application.yml");
        String info = read("community-info/src/main/resources/application.yml");
        String authNacos = read("doc/community-auth-dev.yaml");
        String common = read("doc/common-config.yaml");
        String redis = read("doc/redis-common.yaml");

        assertAll(
                () -> assertTrue(gateway.contains("active: ${SPRING_PROFILES_ACTIVE:dev}")),
                () -> assertTrue(auth.contains("active: ${SPRING_PROFILES_ACTIVE:dev}")),
                () -> assertTrue(info.contains("active: ${SPRING_PROFILES_ACTIVE:dev}")),
                () -> assertTrue(gateway.contains("file-extension: yaml")),
                () -> assertTrue(auth.contains("file-extension: yml")),
                () -> assertTrue(info.contains("file-extension: yaml")),
                () -> assertTrue(authNacos.contains("${JWT_SECRET}")),
                () -> assertTrue(common.contains("${SECURITY_INTERNAL_SECRET}")),
                () -> assertTrue(common.contains("${SENSITIVE_AES_KEY}")),
                () -> assertTrue(common.contains("${SENSITIVE_HMAC_KEY}")),
                () -> assertTrue(common.contains("${GATEWAY_PUBLIC_URL:http://127.0.0.1:9090}")),
                () -> assertTrue(redis.contains("${REDIS_PASSWORD}")),
                () -> assertFalse(common.contains("warnspring:")),
                () -> assertFalse(info.contains("http://ip"))
        );
    }

    @Test
    void localAndNacosDatasourcesUseTheSharedDatabaseEnvironmentVariables() throws Exception {
        String authLocal = read("community-auth/src/main/resources/application-local.yml");
        String infoLocal = read("community-info/src/main/resources/application-local.yml");
        String authNacos = read("doc/community-auth-dev.yaml");
        String infoNacos = read("doc/community-info-dev.yaml");

        assertAll(
                () -> assertTrue(authLocal.contains("${SERVER_ADDRESS:127.0.0.1}")),
                () -> assertTrue(authLocal.contains("username: ${DB_USERNAME:root}")),
                () -> assertTrue(authLocal.contains("password: ${DB_PASSWORD:root}")),
                () -> assertTrue(infoLocal.contains("${SERVER_ADDRESS:127.0.0.1}")),
                () -> assertTrue(infoLocal.contains("username: ${DB_USERNAME:root}")),
                () -> assertTrue(infoLocal.contains("password: ${DB_PASSWORD:root}")),
                () -> assertTrue(authNacos.contains("username: ${DB_USERNAME}")),
                () -> assertTrue(authNacos.contains("password: ${DB_PASSWORD}")),
                () -> assertTrue(infoNacos.contains("username: ${DB_USERNAME}")),
                () -> assertTrue(infoNacos.contains("password: ${DB_PASSWORD}"))
        );
    }

    private static List<Path> configurationFiles() throws Exception {
        try (Stream<Path> files = Files.walk(PROJECT_ROOT)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(ConfigurationContractTest::isInScope)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isInScope(Path path) {
        Path relative = PROJECT_ROOT.relativize(path);
        if (relative.getNameCount() > 0 && relative.getName(0).toString().equals("doc")) {
            return relative.getNameCount() == 2 && relative.getFileName().toString().endsWith(".yaml");
        }

        if (Stream.of(relative).anyMatch(segment -> {
            String name = segment.toString();
            return name.equals("target") || name.equals("build") || name.equals(".git");
        })) {
            return false;
        }

        String fileName = relative.getFileName().toString();
        return fileName.startsWith("application")
                && (fileName.endsWith(".yml") || fileName.endsWith(".yaml"));
    }

    private static void parseEveryDocument(Yaml yaml, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            yaml.loadAll(reader).forEach(ignored -> { });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed parsing YAML: " + PROJECT_ROOT.relativize(file), exception);
        }
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
