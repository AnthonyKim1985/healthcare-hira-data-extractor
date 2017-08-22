package org.bigdatacenter.healthcarehiradataextractor.service;

import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.task.creation.TableCreationTask;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.task.extraction.DataExtractionTask;

public interface RawDataDBService {
    void extractData(DataExtractionTask dataExtractionTask);

    void createTable(TableCreationTask tableCreationTask);
}
