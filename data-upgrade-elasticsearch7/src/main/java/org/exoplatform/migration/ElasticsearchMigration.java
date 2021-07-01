package org.exoplatform.migration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import org.exoplatform.analytics.api.service.StatisticDataProcessorService;
import org.exoplatform.analytics.es.AnalyticsESClient;
import org.exoplatform.analytics.es.AnalyticsIndexingServiceConnector;
import org.exoplatform.analytics.utils.AnalyticsUtils;
import org.exoplatform.commons.search.es.client.ElasticResponse;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.IOUtil;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ElasticsearchMigration extends UpgradeProductPlugin {

  private static final String               QUERY_TARGET_INDEX_PARAM        = "TARGET_INDEX";

  private static final String               QUERY_SOURCE_INDEX_PARAM        = "SOURCE_INDEX";

  private static final String               QUERY_SOURCE_TYPE_PARAM         = "SOURCE_TYPE";

  private static final String               REINDEX_URI                     = "_reindex?require_alias=true&refresh=true";

  private static final Log                  LOG                             =
                                                ExoLogger.getExoLogger(ElasticsearchMigration.class);

  private static final String               SEARCH_INDEX_TYPE_REINDEX_QUERY = "jar:/analytics-index-type-migration.json";

  private static final String               SEARCH_INDEX_REINDEX_QUERY      = "jar:/analytics-index-migration.json";

  private static final String               QUERY_SOURCE_PARAM              = "ES5_HOST";

  private static final String               QUERY_SOCKET_TIMEOUT_PARAM      = "SOCKET_TIMEOUT";

  private static final String               QUERY_CONNECTION_TIMEOUT_PARAM  = "CONNECT_TIMEOUT";

  private static final String               SOURCE_ES_URL_PARAM             = "elasticsearch56.url";

  private static final String               SOCKET_TIMEOUT_PARAM            = "socket.timeout";

  private static final String               CONNECTION_TIMEOUT_PARAM        = "connection.timeout";

  private static final long                 DAY_IN_MS                       = 86400000L;

  private ConfigurationManager              configurationManager;

  private AnalyticsESClient                 analyticsESClient;

  private StatisticDataProcessorService     statisticDataProcessorService;

  private AnalyticsIndexingServiceConnector analyticsIndexingConnector;

  private String                            sourceESUrl;

  private String                            socketTimeout;

  private String                            connectionTimeout;

  private String                            reindexNoTypeQuery;

  private String                            reindexWithTypeQuery;

  public ElasticsearchMigration(ConfigurationManager configurationManager,
                                AnalyticsESClient analyticsESClient,
                                StatisticDataProcessorService statisticDataProcessorService,
                                AnalyticsIndexingServiceConnector analyticsIndexingConnector,
                                InitParams initParams) {
    super(initParams);
    this.analyticsESClient = analyticsESClient;
    this.statisticDataProcessorService = statisticDataProcessorService;
    this.configurationManager = configurationManager;
    this.analyticsIndexingConnector = analyticsIndexingConnector;
    if (initParams != null) {
      if (initParams.getValueParam(SOURCE_ES_URL_PARAM) != null) {
        this.sourceESUrl = initParams.getValueParam(SOURCE_ES_URL_PARAM).getValue();
      }
      if (initParams.getValueParam(SOCKET_TIMEOUT_PARAM) != null) {
        this.socketTimeout = initParams.getValueParam(SOCKET_TIMEOUT_PARAM).getValue();
      }
      if (initParams.getValueParam(CONNECTION_TIMEOUT_PARAM) != null) {
        this.connectionTimeout = initParams.getValueParam(CONNECTION_TIMEOUT_PARAM).getValue();
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return StringUtils.isNotBlank(this.sourceESUrl);
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    boolean upgraded = true;
    if (analyticsESClient.sendIsIndexExistsRequest("profile_alias")) {
      upgraded = upgradeIndex("profile_alias", null, "profile_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("space_alias")) {
      upgraded = upgradeIndex("space_alias", null, "space_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("wiki_alias")) {
      upgraded = upgradeIndex("wiki_v2", "wiki-page", "wiki_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("activity_alias")) {
      upgraded = upgradeIndex("activity_alias", null, "activity_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("news_alias")) {
      upgraded = upgradeIndex("news_alias", null, "news_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("event_alias")) {
      upgraded = upgradeIndex("event_alias", null, "event_alias") && upgraded;
    }
    if (analyticsESClient.sendIsIndexExistsRequest("file_alias")) {
      upgraded = upgradeIndex("file_alias", null, "file_alias") && upgraded;
    }

    upgraded = upgradeAnalyticsIndices() && upgraded;

    if (upgraded) {
      LOG.info("Elasticsearch upgrade proceeded successfully. You can switch off the old Elasticsearch 5.6 !");
    } else {
      throw new IllegalStateException("Elasticsearch upgrade failed due to previous errors");
    }
  }

  private boolean upgradeIndex(String sourceAnalyticsIndex, String sourceAnalyticsType, String targetAnalyticsIndex) {
    LOG.info("START::Index '{}' from ES5 migration to index '{}' on ES7.", sourceAnalyticsIndex, targetAnalyticsIndex);

    long startTime = System.currentTimeMillis();
    try {
      String esQuery = getReindexQuery(StringUtils.isNotBlank(sourceAnalyticsType));
      esQuery = esQuery.replace(QUERY_SOURCE_PARAM, sourceESUrl)
                       .replace(QUERY_SOCKET_TIMEOUT_PARAM, socketTimeout)
                       .replace(QUERY_CONNECTION_TIMEOUT_PARAM, connectionTimeout)
                       .replace(QUERY_SOURCE_INDEX_PARAM, sourceAnalyticsIndex)
                       .replace(QUERY_SOURCE_TYPE_PARAM, sourceAnalyticsType == null ? "" : sourceAnalyticsType)
                       .replace(QUERY_TARGET_INDEX_PARAM, targetAnalyticsIndex);
      ElasticResponse response = analyticsESClient.sendHttpPostRequest(REINDEX_URI, esQuery);
      JSONObject esResponseObject = new JSONObject(response.getMessage());
      long totalObjects = esResponseObject.getLong("total");

      LOG.info("END::Index '{}' migration successfully. The operation took {} milliseconds for '{}' documents.",
               sourceAnalyticsIndex,
               (System.currentTimeMillis() - startTime),
               totalObjects);
      return checkDocCountCoherence(sourceAnalyticsIndex, sourceAnalyticsType, targetAnalyticsIndex);
    } catch (Exception e) {
      LOG.error("Error while migrating index {}", sourceAnalyticsIndex, e);
      return false;
    }
  }

  private boolean upgradeAnalyticsIndices() {
    LOG.info("START::All analytics Indices migration.");
    long startTime = System.currentTimeMillis();
    statisticDataProcessorService.pauseProcessor(AnalyticsUtils.ES_ANALYTICS_PROCESSOR_ID);
    boolean upgraded = true;
    try {
      deleteAnalyticsIndices();
      List<String> analyticsIndices = getAnalyticsIndices(sourceESUrl);
      int analyticsIndicesCount = analyticsIndices.size();
      LOG.info("START::All {} analytics Indices migration.", analyticsIndicesCount);
      String analyticsPrefix = analyticsIndexingConnector.getIndexPrefix() + "_";
      for (int i = 0; i < analyticsIndicesCount; i++) {
        String sourceAnalyticsIndex = analyticsIndices.get(i);
        String targetAnalyticsIndex = getAnalyticsTargetIndexName(analyticsPrefix, sourceAnalyticsIndex);

        upgraded = upgradeAnalyticsIndex(sourceAnalyticsIndex, targetAnalyticsIndex, i, analyticsIndicesCount) && upgraded;
      }
      LOG.info("END::All {} analytics Indices migration successfully. The operation took {} milliseconds.",
               analyticsIndicesCount,
               (System.currentTimeMillis() - startTime));
      return upgraded;
    } catch (Exception e) {
      LOG.error("Error while migrating all analytics indices", e);
      return false;
    } finally {
      statisticDataProcessorService.unpauseProcessor(AnalyticsUtils.ES_ANALYTICS_PROCESSOR_ID);
    }
  }

  private void deleteAnalyticsIndices() throws JSONException {
    List<String> es7AnalyticsIndices = getAnalyticsIndices(null);
    for (String es7AnalyticsIndex : es7AnalyticsIndices) {
      LOG.info("START::Delete automatically created analytics index {} in ES7", es7AnalyticsIndex);
      analyticsESClient.sendHttpDeleteRequest(es7AnalyticsIndex);
      LOG.info("END::Delete automatically created analytics index {} in ES7 successfully", es7AnalyticsIndex);
    }
  }

  private boolean upgradeAnalyticsIndex(String sourceAnalyticsIndex,
                                        String targetAnalyticsIndex,
                                        int i,
                                        int analyticsIndicesCount) {
    try {
      LOG.info("START::{} analytics Index creation: {}/{}.", targetAnalyticsIndex, (i + 1), analyticsIndicesCount);
      analyticsESClient.sendCreateIndexRequest(targetAnalyticsIndex);
      LOG.info("END::{} analytics Index creation: {}/{}.", targetAnalyticsIndex, (i + 1), analyticsIndicesCount);
      return upgradeIndex(sourceAnalyticsIndex, null, targetAnalyticsIndex);
    } catch (Exception e) {
      LOG.info("START::{} analytics Index migration: {}/{}.", targetAnalyticsIndex, (i + 1), analyticsIndicesCount);
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> getAnalyticsIndices(String clientUri) throws JSONException {
    ElasticResponse response = analyticsESClient.sendHttpGetRequest(clientUri, "analytics*/_mapping");
    JSONObject esResponseObject = new JSONObject(response.getMessage());
    List<String> analyticsIndices = new ArrayList<>();
    esResponseObject.keys().forEachRemaining(index -> analyticsIndices.add(index.toString()));
    return analyticsIndices;
  }

  private String getAnalyticsTargetIndexName(String analyticsPrefix, String sourceAnalyticsIndex) {
    String sourceAnalyticsIndexSuffix = sourceAnalyticsIndex.replace(analyticsPrefix, "");
    if (StringUtils.isNumeric(sourceAnalyticsIndexSuffix)) {
      long suffix = Long.parseLong(sourceAnalyticsIndexSuffix);
      long timestamp = suffix * DAY_IN_MS * analyticsESClient.getIndexPerDays();
      return analyticsPrefix + analyticsESClient.getIndexSuffix(timestamp);
    } else {
      return sourceAnalyticsIndex;
    }
  }

  private String getFileContent(String filePath) throws Exception {
    InputStream mappingFileIS = configurationManager.getInputStream(filePath);
    return IOUtil.getStreamContentAsString(mappingFileIS);
  }

  private boolean checkDocCountCoherence(String sourceAnalyticsIndex,
                                         String sourceAnalyticsType,
                                         String targetAnalyticsIndex) throws JSONException {
    analyticsESClient.refreshIndex(targetAnalyticsIndex);
    String sourceAnalyticsIndexType = StringUtils.isBlank(sourceAnalyticsType) ? sourceAnalyticsIndex
                                                                               : sourceAnalyticsIndex + "/"
                                                                                   + sourceAnalyticsType;
    long sourceCount = getDocCount(this.sourceESUrl, sourceAnalyticsIndexType);
    long targetCount = getDocCount(null, targetAnalyticsIndex);
    if (targetCount < sourceCount) {
      LOG.error("Check Migrated Doc COUNT Error, detected {} on source index '{}' on ES5 and {} on target index '{}' ES7",
                sourceCount,
                sourceAnalyticsIndexType,
                targetCount,
                targetAnalyticsIndex);
      return false;
    }
    LOG.info("REPORT::Check Migrated Doc COUNT Success, detected {} on source index '{}' on ES5 and {} on target index '{}' ES7",
             sourceCount,
             sourceAnalyticsIndexType,
             targetCount,
             targetAnalyticsIndex);
    return true;
  }

  private long getDocCount(String urlClient, String index) throws JSONException {
    ElasticResponse response = analyticsESClient.sendHttpGetRequest(urlClient, index + "/_count");
    JSONObject responseObject = new JSONObject(response.getMessage());
    return responseObject.getLong("count");
  }

  public String getReindexQuery(boolean withType) throws Exception {
    if (withType) {
      if (StringUtils.isBlank(reindexWithTypeQuery)) {
        reindexWithTypeQuery = getFileContent(SEARCH_INDEX_TYPE_REINDEX_QUERY);
      }
      return reindexWithTypeQuery;
    } else {
      if (StringUtils.isBlank(reindexNoTypeQuery)) {
        reindexNoTypeQuery = getFileContent(SEARCH_INDEX_REINDEX_QUERY);
      }
      return reindexNoTypeQuery;
    }
  }

}
