package com.measure.community.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.measure.community.common.enums.DataScope;
import com.measure.community.common.utils.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/** 只对 PopulationMapper 的查询注入行级数据范围 WHERE。 */
public class PopulationDataPermissionHandler implements DataPermissionHandler {

    private static final String POPULATION_MAPPER = "com.measure.community.info.mapper.PopulationMapper";

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        if (mappedStatementId == null || !mappedStatementId.startsWith(POPULATION_MAPPER)) {
            return where; // 白名单外：原样返回，不注入
        }
        if (UserContextHolder.get() == null) {
            return where; // 无上下文=系统调用
        }
        DataScope scope = DataScope.fromCode(UserContextHolder.getDataScope());
        // 注入的是裸列名 grid_id / create_by，依赖 PopulationMapper 是单表 BaseMapper（无 join/子查询）。
        // 若将来该 mapper 增加联表或白名单扩展到多表，需给这些列加表限定名以免歧义。
        String cond;
        switch (scope) {
            case ALL -> { return where; }
            case GRID -> {
                Long grid = UserContextHolder.getGridId();
                cond = grid != null ? "grid_id = " + grid : "1 = 0";
            }
            case SELF -> {
                String uid = UserContextHolder.getUserId();
                cond = "create_by = '" + (uid == null ? "" : uid.replace("'", "''")) + "'";
            }
            default -> { // 层级档 DISTRICT/STREET/COMMUNITY：按用户节点 orgPath 展开为其下所有 GRID
                String path = UserContextHolder.getOrgPath();
                cond = (path != null && !path.isBlank())
                        ? "grid_id IN (SELECT id FROM sys_org WHERE type = 'GRID' AND path LIKE '"
                            + path.replace("'", "''") + "%')"
                        : "1 = 0";
            }
        }
        try {
            Expression scopeExpr = CCJSqlParserUtil.parseCondExpression(cond);
            if (where == null) return scopeExpr;
            return new net.sf.jsqlparser.expression.operators.conditional.AndExpression(where, scopeExpr);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to build data-scope condition: " + cond, ex);
        }
    }
}
