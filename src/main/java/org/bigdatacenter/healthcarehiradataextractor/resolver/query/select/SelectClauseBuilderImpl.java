package org.bigdatacenter.healthcarehiradataextractor.resolver.query.select;

import org.springframework.stereotype.Component;

@Component
public class SelectClauseBuilderImpl implements SelectClauseBuilder {
    @Override
    public String buildClause(String dbName, String tableName) {
        return String.format("SELECT * FROM %s.%s", dbName, tableName);
    }

    @Override
    public String buildClause(String dbName, String tableName, String projections) {
        return String.format("SELECT %s FROM %s.%s", projections, dbName, tableName);
    }
}