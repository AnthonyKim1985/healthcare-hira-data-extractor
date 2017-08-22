package org.bigdatacenter.healthcarehiradataextractor.resolver.extraction;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.ExtractionParameter;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.ExtractionRequest;

public interface ExtractionRequestResolver {
    ExtractionRequest buildExtractionRequest(ExtractionParameter extractionParameter);
}
