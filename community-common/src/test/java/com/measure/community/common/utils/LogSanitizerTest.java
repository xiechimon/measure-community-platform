package com.measure.community.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LogSanitizerTest {

    @Test
    void sensitiveValuesAreRedactedWithoutDroppingOtherKeys() {
        String raw = "name=张三&idCard=330106199001011234&token=abc&page=1";

        String safe = LogSanitizer.sanitizeQuery(raw);

        assertEquals("name=张三&idCard=***&token=***&page=1", safe);
        assertFalse(safe.contains("330106199001011234"));
        assertFalse(safe.contains("abc"));
    }

    @Test
    void nullAndEmptyAreReportedAsEmpty() {
        assertEquals("EMPTY", LogSanitizer.sanitizeQuery(null));
        assertEquals("EMPTY", LogSanitizer.sanitizeQuery(""));
    }

    @Test
    void matchingIsCaseInsensitiveAndKeepsEmptySegmentsAndAdditionalEquals() {
        String raw = "PASSWORD=first=second&&Phone=&plain=value=kept&flag&SECRET";

        assertEquals("PASSWORD=***&&Phone=***&plain=value=kept&flag&SECRET", LogSanitizer.sanitizeQuery(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "token", "secret", "idcard", "phone"})
    void everySensitiveKeyIsRedactedIncludingValuesWithAdditionalEquals(String key) {
        String raw = key + "=first=second";

        assertEquals(key + "=***", LogSanitizer.sanitizeQuery(raw));
    }

    @Test
    void sensitiveKeysAreMatchedCaseInsensitively() {
        assertEquals("ToKeN=***", LogSanitizer.sanitizeQuery("ToKeN=first=second"));
    }

    @Test
    void encodedAsciiSensitiveKeysAreRedactedWithoutDecodingValues() {
        String raw = "id%43ard=card-value&%69%64%43%61%72%64=other-value&token%ZZ=unchanged&%E4%BD%A0=value";

        assertEquals("id%43ard=***&%69%64%43%61%72%64=***&token%ZZ=unchanged&%E4%BD%A0=value",
                LogSanitizer.sanitizeQuery(raw));
    }
}
