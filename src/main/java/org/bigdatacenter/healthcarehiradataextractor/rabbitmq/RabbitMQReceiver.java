package org.bigdatacenter.healthcarehiradataextractor.rabbitmq;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.ExtractionRequest;

public interface RabbitMQReceiver {
    void runReceiver(ExtractionRequest extractionRequest);
}
