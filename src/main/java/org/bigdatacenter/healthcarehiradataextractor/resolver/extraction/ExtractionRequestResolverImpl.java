package org.bigdatacenter.healthcarehiradataextractor.resolver.extraction;

import org.bigdatacenter.healthcarehiradataextractor.api.caller.DataIntegrationPlatformAPICaller;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.ExtractionParameter;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.info.AdjacentTableInfo;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.map.ParameterKey;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.parameter.map.ParameterValue;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.ExtractionRequest;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.parameter.ExtractionRequestParameter;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.query.JoinParameter;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.task.QueryTask;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.task.creation.TableCreationTask;
import org.bigdatacenter.healthcarehiradataextractor.domain.extraction.request.task.extraction.DataExtractionTask;
import org.bigdatacenter.healthcarehiradataextractor.domain.transaction.TrRequestInfo;
import org.bigdatacenter.healthcarehiradataextractor.resolver.extraction.parameter.ExtractionRequestParameterResolver;
import org.bigdatacenter.healthcarehiradataextractor.resolver.query.join.JoinClauseBuilder;
import org.bigdatacenter.healthcarehiradataextractor.resolver.query.select.SelectClauseBuilder;
import org.bigdatacenter.healthcarehiradataextractor.resolver.query.where.WhereClauseBuilder;
import org.bigdatacenter.healthcarehiradataextractor.util.CommonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExtractionRequestResolverImpl implements ExtractionRequestResolver {
    private static final Logger logger = LoggerFactory.getLogger(ExtractionRequestResolverImpl.class);
    private static final String currentThreadName = Thread.currentThread().getName();

    private final SelectClauseBuilder selectClauseBuilder;

    private final WhereClauseBuilder whereClauseBuilder;

    private final JoinClauseBuilder joinClauseBuilder;

    private final ExtractionRequestParameterResolver extractionRequestParameterResolver;

    private final DataIntegrationPlatformAPICaller dataIntegrationPlatformAPICaller;

    @Autowired
    public ExtractionRequestResolverImpl(SelectClauseBuilder selectClauseBuilder,
                                         WhereClauseBuilder whereClauseBuilder,
                                         JoinClauseBuilder joinClauseBuilder,
                                         ExtractionRequestParameterResolver extractionRequestParameterResolver,
                                         DataIntegrationPlatformAPICaller dataIntegrationPlatformAPICaller) {
        this.selectClauseBuilder = selectClauseBuilder;
        this.whereClauseBuilder = whereClauseBuilder;
        this.joinClauseBuilder = joinClauseBuilder;
        this.extractionRequestParameterResolver = extractionRequestParameterResolver;
        this.dataIntegrationPlatformAPICaller = dataIntegrationPlatformAPICaller;
    }

    @Override
    public ExtractionRequest buildExtractionRequest(ExtractionParameter extractionParameter) {
        if (extractionParameter == null)
            throw new NullPointerException("The extractionParameter is null.");

        try {
            final TrRequestInfo requestInfo = extractionParameter.getRequestInfo();
            final Integer dataSetUID = requestInfo.getDataSetUID();
            final String databaseName = extractionParameter.getDatabaseName();
            final String joinCondition = requestInfo.getJoinCondition();

            final ExtractionRequestParameter extractionRequestParameter = extractionRequestParameterResolver.buildRequestParameter(extractionParameter);

            final Map<Integer/* Year */, Map<ParameterKey, List<ParameterValue>>> yearParameterMap = extractionRequestParameter.getYearParameterMap();
            final Map<Integer/* Year */, Set<ParameterKey>> yearJoinKeyMap = extractionRequestParameter.getYearJoinKeyMap();
            final Map<Integer/* Year */, Set<AdjacentTableInfo>> yearAdjacentTableInfoMap = extractionRequestParameter.getYearAdjacentTableInfoMap();

            final List<QueryTask> queryTaskList = new ArrayList<>();
            final Map<Integer/* Year */, JoinParameter> joinParameterMapForExtraction = new HashMap<>();

            //
            // TODO: 1. 추출 연산을 위한 임시 테이블들을 생성한다.
            //
            for (Integer year : yearParameterMap.keySet()) {
                Map<ParameterKey, List<ParameterValue>> parameterMap = yearParameterMap.get(year);
                Set<ParameterKey> joinTargetKeySet = yearJoinKeyMap.get(year);

                //
                // TODO: 1.1. 조인 대상키가 없으면 해당 연도를 스킵힌다.
                //
                if (joinTargetKeySet == null || joinTargetKeySet.isEmpty())
                    continue;

                //
                // TODO: 1.2. 임시 테이블 생성 쿼리를 생성한다. (유니크 컬럼을 갖고 있는 테이블만 대상)
                //
                List<JoinParameter> joinParameterList = new ArrayList<>();
                for (ParameterKey parameterKey : joinTargetKeySet) {
                    final String tableName = parameterKey.getTableName();

                    final String selectClause = selectClauseBuilder.buildClause(databaseName, tableName, parameterKey.getHeader(), Boolean.FALSE);
                    final String whereClause = whereClauseBuilder.buildClause(parameterMap.get(parameterKey));
                    final String query = String.format("%s %s", selectClause, whereClause);
                    logger.debug(String.format("(dataSetUID=%d / threadName=%s) - query: %s", dataSetUID, currentThreadName, query));

                    final String extrDbName = String.format("%s_extracted", databaseName);
                    final String extrTableName = String.format("%s_%s", databaseName, CommonUtil.getHashedString(query));
                    final String dbAndHashedTableName = String.format("%s.%s", extrDbName, extrTableName);
                    logger.debug(String.format("(dataSetUID=%d / threadName=%s) - dbAndHashedTableName: %s", dataSetUID, currentThreadName, dbAndHashedTableName));

                    TableCreationTask tableCreationTask = new TableCreationTask(dbAndHashedTableName, query);

                    queryTaskList.add(new QueryTask(tableCreationTask, null));
                    joinParameterList.add(new JoinParameter(extrDbName, extrTableName, joinCondition, "spec_id_sno"));
                }

                //
                // TODO: 1.3. 임시 데이블들의 조인 연산을 위한 테이블 생성 쿼리를 생성한다.
                //
                final String joinQuery = joinClauseBuilder.buildClause(joinParameterList);
                logger.debug(String.format("(dataSetUID=%d / threadName=%s) - joinQuery: %s", dataSetUID, currentThreadName, joinQuery));

                final String joinDbName = String.format("%s_join_%s_integrated", databaseName, joinCondition);
                final String joinTableName = String.format("%s_%s", databaseName, CommonUtil.getHashedString(joinQuery));
                final String dbAndHashedTableName = String.format("%s.%s", joinDbName, joinTableName);

                TableCreationTask tableCreationTask = new TableCreationTask(dbAndHashedTableName, joinQuery);
                queryTaskList.add(new QueryTask(tableCreationTask, null));

                JoinParameter joinParameter = new JoinParameter(joinDbName, joinTableName, joinCondition, joinCondition);
                joinParameterMapForExtraction.put(year, joinParameter);
            }

            //
            // TODO: 2. 원시 데이터 셋 테이블과 조인연산 수행을 위한 쿼리 및 데이터 추출 쿼리를 생성한다.
            //
            for (Integer dataSetYear : joinParameterMapForExtraction.keySet()) {
                final JoinParameter targetJoinParameter = joinParameterMapForExtraction.get(dataSetYear);
                final Set<AdjacentTableInfo> adjacentTableInfoSet = yearAdjacentTableInfoMap.get(dataSetYear);
                queryTaskList.addAll(getJoinQueryTasks(adjacentTableInfoSet, targetJoinParameter, databaseName, joinCondition, requestInfo.getDataSetUID()));
            }

            final ExtractionRequest extractionRequest = new ExtractionRequest(databaseName, requestInfo, queryTaskList);
            logger.info(String.format("(dataSetUID=%d / threadName=%s) - ExtractionRequest: %s", dataSetUID, currentThreadName, extractionRequest));

            return extractionRequest;
        } catch (Exception e) {
            e.printStackTrace();
            throw new NullPointerException(e.getMessage());
        }
    }

    private List<QueryTask> getJoinQueryTasks(Set<AdjacentTableInfo> adjacentTableInfoSet, JoinParameter targetJoinParameter, String databaseName, String joinCondition, Integer dataSetUID) {
        List<QueryTask> queryTaskList = new ArrayList<>();

        try {
            for (AdjacentTableInfo adjacentTableInfo : adjacentTableInfoSet) {
                final Integer dataSetYear = adjacentTableInfo.getDataSetYear();
                final String tableName = adjacentTableInfo.getTableName();
                final String header = adjacentTableInfo.getHeader();

                JoinParameter sourceJoinParameter = new JoinParameter(databaseName, tableName, header, joinCondition);

                final String joinQuery = joinClauseBuilder.buildClause(sourceJoinParameter, targetJoinParameter, Boolean.FALSE);
                final String joinDbName = String.format("%s_join_%s_integrated", databaseName, joinCondition);
                final String joinTableName = String.format("%s_%s", databaseName, CommonUtil.getHashedString(joinQuery));
                final String dbAndHashedTableName = String.format("%s.%s", joinDbName, joinTableName);
                final String extractionQuery = selectClauseBuilder.buildClause(joinDbName, joinTableName, header, Boolean.FALSE);

                TableCreationTask tableCreationTask = new TableCreationTask(dbAndHashedTableName, joinQuery);
                DataExtractionTask dataExtractionTask = new DataExtractionTask(tableName/*Data File Name*/, CommonUtil.getHdfsLocation(dbAndHashedTableName, dataSetUID), extractionQuery, header);

                queryTaskList.add(new QueryTask(tableCreationTask, dataExtractionTask));

                //
                // TODO: Make ykiho Extraction tasks
                //
                if (tableName.contains("_t20_")) {
                    final String[] extraTablePrefixes = {"ykiho"};

                    for (String extraTablePrefix : extraTablePrefixes) {
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The joinDbName.joinTableName for extra-extraction: %s.%s", dataSetUID, currentThreadName, joinDbName, joinTableName));

                        final String extraFileName = String.format("%s_%s_%d", databaseName, extraTablePrefix, dataSetYear);
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The fileName for extra-extraction: %s", dataSetUID, currentThreadName, extraFileName));

                        final String extraHdfsLocation = CommonUtil.getHdfsLocation(String.format("%s.%s", databaseName, extraFileName), dataSetUID);
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The hdfsLocation for extra-extraction: %s", dataSetUID, currentThreadName, extraHdfsLocation));

                        final String extraHeader = dataIntegrationPlatformAPICaller.callReadProjectionNames(dataSetUID, extraFileName, dataSetYear);
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The header for extra-extraction: %s", dataSetUID, currentThreadName, extraHeader));

                        final String extraQuery = selectClauseBuilder.buildClause(joinDbName, joinTableName, extraHeader, Boolean.TRUE);
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The query for extra-extraction: %s", dataSetUID, currentThreadName, extraQuery));

                        final QueryTask queryTask = new QueryTask(null, new DataExtractionTask(extraFileName/*Data File Name*/, extraHdfsLocation, extraQuery, extraHeader));
                        logger.debug(String.format("(dataSetUID=%d / threadName=%s) - The queryTask for extra-extraction: %s", dataSetUID, currentThreadName, queryTask));

                        queryTaskList.add(queryTask);
                    }
                }
            }

            logger.info(String.format("(dataSetUID=%d / threadName=%s) - QueryTaskList For Join Operation: %s", dataSetUID, currentThreadName, queryTaskList));

            return queryTaskList;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
}