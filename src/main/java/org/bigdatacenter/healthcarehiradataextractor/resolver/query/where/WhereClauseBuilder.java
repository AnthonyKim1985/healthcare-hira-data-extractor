package org.bigdatacenter.healthcarehiradataextractor.resolver.query.where;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.map.ParameterValue;

import java.util.List;

public interface WhereClauseBuilder {
    String buildClause(List<ParameterValue> parameterValueList);
}