package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void propagatesUserContextToChildThread() throws Exception {
        Map<String, String> user = new HashMap<>();
        user.put("id", "u1");
        UserContextHolder.set(user);

        AtomicReference<String> seen = new AtomicReference<>("none");
        Runnable decorated = new ContextTaskDecorator().decorate(
                () -> seen.set(UserContextHolder.getUserId()));

        Thread t = new Thread(decorated);
        t.start();
        t.join();

        assertEquals("u1", seen.get());
    }
}
