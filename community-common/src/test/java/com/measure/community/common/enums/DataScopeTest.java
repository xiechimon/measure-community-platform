package com.measure.community.common.enums;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DataScopeTest {
    @Test
    void resolvePicksBroadest() {
        assertEquals(DataScope.ALL, DataScope.resolve(List.of("SELF", "ALL", "GRID")));
        assertEquals(DataScope.GRID, DataScope.resolve(List.of("SELF", "GRID")));
        assertEquals(DataScope.SELF, DataScope.resolve(List.of("SELF")));
    }
    @Test
    void unknownAndEmptyFailClosedToSelf() {
        assertEquals(DataScope.SELF, DataScope.resolve(List.of("DISTRICT", "CUSTOM")));
        assertEquals(DataScope.SELF, DataScope.resolve(List.of()));
        assertEquals(DataScope.SELF, DataScope.fromCode(null));
        assertEquals(DataScope.SELF, DataScope.fromCode("STREET"));
    }
}
