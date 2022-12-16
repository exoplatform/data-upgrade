package org.exoplatform.elastic.upgrade;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.es.AnalyticsESClient;
import org.exoplatform.commons.search.es.client.ElasticResponse;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ElasticsearchMigration extends UpgradeProductPlugin {

  private static final Log    LOG             = ExoLogger.getExoLogger(ElasticsearchMigration.class);

  private static final String OLD_INDEX_PARAM = "oldIndex";

  private static final String NEW_INDEX_PARAM = "newIndex";

  private static final String REINDEX_URI     = "_reindex";

  private AnalyticsESClient   analyticsESClient;

  private String              oldIndex;

  private String              newIndex;

  public ElasticsearchMigration(AnalyticsESClient analyticsESClient, InitParams initParams) {
    super(initParams);
    this.analyticsESClient = analyticsESClient;
    if (initParams != null) {
      this.oldIndex = initParams.getValueParam(OLD_INDEX_PARAM).getValue();
      this.newIndex = initParams.getValueParam(NEW_INDEX_PARAM).getValue();
    }
  }

  @Override
  public boolean isEnabled() {
    LOG.info("ElasticsearchMigration from {} to {}", this.oldIndex, this.newIndex);
    return StringUtils.isNotBlank(this.oldIndex) && StringUtils.isNotBlank(this.newIndex);
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
    try {
      reindex();
    } catch (Exception e) {
      LOG.info("Reindexing proceeded successfully.");
    }
  }

  public void reindex() {
    long startTime = System.currentTimeMillis();
    try {
      LOG.info("Reindexing index  from old index {} to new index {}", oldIndex, newIndex);
      String request = "{\n" + "\t\"source\": {\n" + "\t\t\"index\": \"" + oldIndex + "\"\n" + "\t},\n" + "\t\"dest\": {\n"
          + "\t\t\"index\": \"" + newIndex + "\"\n" + "\t}\n" + "}";
      ElasticResponse response = analyticsESClient.sendHttpPostRequest(REINDEX_URI, request);

      if (response.getStatusCode() != HttpStatus.SC_OK) {
        throw new IllegalStateException("Cannot index alias from old index " + oldIndex + " to new index " + newIndex
            + ", response code = " + response.getStatusCode() + " message = " + response.getMessage());
      } else {
        LOG.info("Reindexation finished for index  from old index {} to new index {}", oldIndex, newIndex);
        LOG.info("START::Delete files index {}", oldIndex);
        analyticsESClient.sendHttpDeleteRequest(oldIndex);
        LOG.info("END::Delete old files index {} successfully", oldIndex);
        LOG.info("END::Index '{}' migration to {} successfully. The operation took {} milliseconds.",
                 oldIndex,
                 newIndex,
                 (System.currentTimeMillis() - startTime));
      }
    } catch (Exception e) {
      LOG.error("An error occurred while reindexing index {} from {} ", newIndex, oldIndex, e);
    }
  }
}
