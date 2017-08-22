package org.bigdatacenter.healthcarehiradataextractor.resolver.extraction.parameter;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.ExtractionParameter;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.parameter.ExtractionRequestParameter;

public interface ExtractionRequestParameterResolver {
    ExtractionRequestParameter buildRequestParameter(ExtractionParameter extractionParameter);
}
