package com.measure.community.common.enums;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataScopeTest {
    @Test
    void resolvePicksBroadest() {
        assertEquals(DataScope.ALL, DataScope.resolve(List.of("SELF", "ALL", "GRID")));
        assertEquals(DataScope.GRID, DataScope.resolve(List.of("SELF", "GRID")));
        assertEquals(DataScope.SELF, DataScope.resolve(List.of("SELF")));
    }
    @Test
    void unknownAndEmptyFailClosedToSelf() {
        assertEquals(DataScope.SELF, DataScope.resolve(List.of("CUSTOM", "BOGUS")));
        assertEquals(DataScope.SELF, DataScope.resolve(List.of()));
        assertEquals(DataScope.SELF, DataScope.fromCode(null));
        assertEquals(DataScope.SELF, DataScope.fromCode("CUSTOM"));
    }
    @Test
    void resolveAcrossSixTiers() {
        assertEquals(DataScope.ALL, DataScope.resolve(java.util.List.of("COMMUNITY","ALL","GRID")));
        assertEquals(DataScope.STREET, DataScope.resolve(java.util.List.of("COMMUNITY","STREET","GRID")));
        assertEquals(DataScope.COMMUNITY, DataScope.resolve(java.util.List.of("COMMUNITY","GRID","SELF")));
    }
    @Test
    void fromCodeSixTiersAndFailClosed() {
        assertEquals(DataScope.DISTRICT, DataScope.fromCode("DISTRICT"));
        assertEquals(DataScope.STREET, DataScope.fromCode("STREET"));
        assertEquals(DataScope.COMMUNITY, DataScope.fromCode("COMMUNITY"));
        assertEquals(DataScope.SELF, DataScope.fromCode("CUSTOM"));
        assertEquals(DataScope.SELF, DataScope.fromCode("nonsense"));
    }
    @Test
    void isHierarchicalOnlyForDistrictStreetCommunity() {
        assertTrue(DataScope.DISTRICT.isHierarchical());
        assertTrue(DataScope.STREET.isHierarchical());
        assertTrue(DataScope.COMMUNITY.isHierarchical());
        assertFalse(DataScope.ALL.isHierarchical());
        assertFalse(DataScope.GRID.isHierarchical());
        assertFalse(DataScope.SELF.isHierarchical());
    }
}
