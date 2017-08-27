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
                                         DataIntegrationPlatformAPICaller dataIntegrationPlatformAPICaller)
    {
        this.selectClauseBuilder = selectClauseBuilder;
        this.whereClauseBuilder = whereClauseBuilder;
        this.joinClauseBuilder = joinClauseBuilder;
        this.extractionRequestParameterResolver = extractionRequestParameterResolver;
        this.dataIntegrationPlatformAPICaller = dataIntegrationPlatformAPICaller;
    }

    @Override
    public ExtractionRequest buildExtractionRequest(ExtractionParameter extractionParameter) {
        if (extractionParameter == null)
            throw new NullPointerException(String.format("%s - extractionParameter is null.", currentThreadName));

        try {
            final TrRequestInfo requestInfo = extractionParameter.getRequestInfo();
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
                    logger.info(String.format("%s - query: %s", currentThreadName, query));

                    final String extrDbName = String.format("%s_extracted", databaseName);
                    final String extrTableName = String.format("%s_%s", databaseName, CommonUtil.getHashedString(query));
                    final String dbAndHashedTableName = String.format("%s.%s", extrDbName, extrTableName);
                    logger.info(String.format("%s - dbAndHashedTableName: %s", currentThreadName, dbAndHashedTableName));

                    TableCreationTask tableCreationTask = new TableCreationTask(dbAndHashedTableName, query);

                    queryTaskList.add(new QueryTask(tableCreationTask, null));
                    joinParameterList.add(new JoinParameter(extrDbName, extrTableName, joinCondition, "key_seq"));
                }

                //
                // TODO: 1.3. 임시 데이블들의 조인 연산을 위한 테이블 생성 쿼리를 생성한다.
                //
                final String joinQuery = joinClauseBuilder.buildClause(joinParameterList);
                logger.info(String.format("%s - joinQuery: %s", currentThreadName, joinQuery));

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

            logger.info(String.format("%s - queryTaskList: %s", currentThreadName, queryTaskList));

            return new ExtractionRequest(databaseName, requestInfo, queryTaskList);
        } catch (Exception e) {
            e.printStackTrace();
            throw new NullPointerException(e.getMessage());
        }
    }

    private List<QueryTask> getJoinQueryTasks(Set<AdjacentTableInfo> adjacentTableInfoSet, JoinParameter targetJoinParameter, String databaseName, String joinCondition, Integer dataSetUID) {
        List<QueryTask> queryTaskList = new ArrayList<>();

        for (AdjacentTableInfo adjacentTableInfo : adjacentTableInfoSet) {
            final Integer dataSetYear = adjacentTableInfo.getDataSetYear();
            final String tableName = adjacentTableInfo.getTableName();
            final String header = adjacentTableInfo.getHeader();

            JoinParameter sourceJoinParameter = new JoinParameter(databaseName, tableName, header, joinCondition);

            final String joinQuery = joinClauseBuilder.buildClause(sourceJoinParameter, targetJoinParameter);
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
                    logger.info(String.format("%s - ex_joinDbName: %s", currentThreadName, joinDbName));
                    logger.info(String.format("%s - ex_joinTableName: %s", currentThreadName, joinTableName));

                    final String extraFileName = String.format("%s_%s_%d", databaseName, extraTablePrefix, dataSetYear);
                    logger.info(String.format("%s - extraFileName: %s", currentThreadName, extraFileName));

                    final String extraHdfsLocation = CommonUtil.getHdfsLocation(String.format("%s.%s", databaseName, extraFileName), dataSetUID);
                    logger.info(String.format("%s - extraHdfsLocation: %s", currentThreadName, extraHdfsLocation));

                    final String extraHeader = dataIntegrationPlatformAPICaller.callReadProjectionNames(dataSetUID, extraFileName);
                    logger.info(String.format("%s - extraHeader: %s", currentThreadName, extraHeader));

                    final String extraQuery = selectClauseBuilder.buildClause(joinDbName, joinTableName, extraHeader, Boolean.TRUE);
                    logger.info(String.format("%s - extraQuery: %s", currentThreadName, extraQuery));

                    queryTaskList.add(new QueryTask(null, new DataExtractionTask(extraFileName/*Data File Name*/, extraHdfsLocation, extraQuery, extraHeader)));
                }
            }
        }

        return queryTaskList;
    }
}