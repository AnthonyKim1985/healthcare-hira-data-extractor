package org.bigdatacenter.healthcarehiradataextractor.resolver.query.join;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.query.JoinParameter;

import java.util.List;

public interface JoinClauseBuilder {
    String buildClause(List<JoinParameter> joinParameterList);
    String buildClause(JoinParameter sourceJoinParameter, JoinParameter targetJoinParameter);
}
