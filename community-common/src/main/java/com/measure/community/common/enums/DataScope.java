package com.measure.community.common.enums;

import java.util.Collection;

/** 行级数据范围。ordinal 从宽到窄：ALL>DISTRICT>STREET>COMMUNITY>GRID>SELF。 */
public enum DataScope {
    ALL, DISTRICT, STREET, COMMUNITY, GRID, SELF;

    /** 精确映射六档；CUSTOM/null/未知 → SELF（fail-closed）。 */
    public static DataScope fromCode(String code) {
        if (code == null) return SELF;
        try {
            return DataScope.valueOf(code);
        } catch (IllegalArgumentException e) {
            return SELF;
        }
    }

    /** 多角色取最宽档（ordinal 最小）。 */
    public static DataScope resolve(Collection<String> roleScopes) {
        DataScope best = SELF;
        if (roleScopes != null) {
            for (String s : roleScopes) {
                DataScope d = fromCode(s);
                if (d.ordinal() < best.ordinal()) best = d;
            }
        }
        return best;
    }

    /** 层级档：DISTRICT/STREET/COMMUNITY（需按用户节点 orgPath 展开为网格集合）。 */
    public boolean isHierarchical() {
        return this == DISTRICT || this == STREET || this == COMMUNITY;
    }
}
