package org.bigdatacenter.healthcarehiradataextractor.resolver.query.where;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.map.ParameterValue;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WhereClauseBuilderImpl implements WhereClauseBuilder {
    @Override
    public String buildClause(List<ParameterValue> parameterValueList) {
        if (parameterValueList == null || parameterValueList.isEmpty())
            return "";

        final int parameterValueListSize = parameterValueList.size();
        final StringBuilder whereClauseBuilder = new StringBuilder("WHERE");

        for (int i = 0; i < parameterValueListSize; i++) {
            ParameterValue parameterValue = parameterValueList.get(i);

            final String columnName = parameterValue.getColumnName().toLowerCase();
            final String columnValue = String.format("'%s'", parameterValue.getColumnValue().replaceAll("[,]", "','"));
            whereClauseBuilder.append(String.format(" %s IN (%s)", columnName, columnValue));

            if (i < parameterValueListSize - 1)
                whereClauseBuilder.append(" AND ");
        }

        return whereClauseBuilder.toString();
    }
}