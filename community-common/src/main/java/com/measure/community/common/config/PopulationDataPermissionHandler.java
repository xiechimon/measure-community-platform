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
        String cond;
        switch (scope) {
            case ALL -> { return where; }
            case GRID -> {
                Long grid = UserContextHolder.getGridId();
                cond = grid != null ? "grid_id = " + grid : "1 = 0";
            }
            default -> { // SELF
                String uid = UserContextHolder.getUserId();
                cond = "create_by = '" + (uid == null ? "" : uid.replace("'", "''")) + "'";
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
