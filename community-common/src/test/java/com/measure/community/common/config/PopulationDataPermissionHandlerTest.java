package com.measure.community.common.config;

import com.measure.community.common.utils.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PopulationDataPermissionHandlerTest {
    private static final String POP = "com.measure.community.info.mapper.PopulationMapper.selectPage";
    private final PopulationDataPermissionHandler h = new PopulationDataPermissionHandler();

    @AfterEach
    void tearDown() { UserContextHolder.clear(); }

    private void ctx(String scope, String gridId, String userId) {
        Map<String,String> m = new HashMap<>();
        m.put("id", userId); if (gridId != null) m.put("gridId", gridId); m.put("dataScope", scope);
        UserContextHolder.set(m);
    }

    @Test
    void gridScopeInjectsGridEq() {
        ctx("GRID", "1001", "7");
        Expression e = h.getSqlSegment(null, POP);
        assertEquals("grid_id = 1001", e.toString());
    }
    @Test
    void selfScopeInjectsCreateByEq() {
        ctx("SELF", null, "7");
        assertEquals("create_by = '7'", h.getSqlSegment(null, POP).toString());
    }
    @Test
    void gridWithNullGridIsImpossible() {
        ctx("GRID", null, "7");
        assertEquals("1 = 0", h.getSqlSegment(null, POP).toString());
    }
    @Test
    void allScopeInjectsNothing() {
        ctx("ALL", null, "7");
        assertNull(h.getSqlSegment(null, POP));
    }
    @Test
    void nonPopulationStatementUntouched() {
        ctx("GRID", "1001", "7");
        assertNull(h.getSqlSegment(null, "com.measure.community.auth.mapper.SysUserMapper.selectById"));
    }
    @Test
    void noContextInjectsNothing() {
        assertNull(h.getSqlSegment(null, POP));
    }
}
