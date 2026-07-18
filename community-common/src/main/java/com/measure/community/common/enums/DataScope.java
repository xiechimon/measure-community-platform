package com.measure.community.common.enums;

import java.util.Collection;

/** 行级数据范围（本片只强制扁平三档；未实现档位 fail-closed 回落 SELF）。 */
public enum DataScope {
    ALL, GRID, SELF;

    public static DataScope fromCode(String code) {
        if ("ALL".equals(code)) return ALL;
        if ("GRID".equals(code)) return GRID;
        return SELF; // SELF / null / 未实现档位(COMMUNITY/STREET/DISTRICT/CUSTOM) 一律最窄
    }

    /** 多角色取最宽档：ALL > GRID > SELF。 */
    public static DataScope resolve(Collection<String> roleScopes) {
        DataScope best = SELF;
        if (roleScopes != null) {
            for (String s : roleScopes) {
                DataScope d = fromCode(s);
                if (d == ALL) return ALL;
                if (d == GRID) best = GRID;
            }
        }
        return best;
    }
}
