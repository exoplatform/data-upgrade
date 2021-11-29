package org.exoplatform.wiki.upgrade;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.analytics.es.AnalyticsESClient;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.search.es.client.ElasticClientException;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wiki.jpa.dao.PageDAO;
import org.exoplatform.wiki.jpa.search.WikiPageIndexingServiceConnector;

public class ElasticsearchMigration extends UpgradeProductPlugin {

  public static final String      NOTES_ES_UPGRADE_SCOPE_NAME        = " NOTES_ES_UPGRADE_SCOPE";

  public static final String      NOTES_ES_UPGRADE_CONTEXT_NAME      = " NOTES_ES_UPGRADE_CONTEXT";

  public static final Context     NOTES_ES_UPGRADE_CONTEXT           = Context.GLOBAL.id(NOTES_ES_UPGRADE_CONTEXT_NAME);

  public static final Scope       NOTES_ES_UPGRADE_SCOPE             = Scope.APPLICATION.id(NOTES_ES_UPGRADE_SCOPE_NAME);

  public static final String      NOTES_ES_UPGRADE_SETTINGS_KEY_NAME = "NOTES_ES_UPGRADE_SETTINGS";

  private static final Log        LOG                                = ExoLogger.getExoLogger(ElasticsearchMigration.class);

  private static final String     SOURCE_ES_URL_PARAM                = "elasticsearch.url";

  private final AnalyticsESClient analyticsESClient;

  private final PageDAO           pageDAO;

  private final IndexingService   indexingService;

  private final SettingService    settingService;

  private String                  sourceESUrl;

  public ElasticsearchMigration(AnalyticsESClient analyticsESClient,
                                IndexingService indexingService,
                                PageDAO pageDAO,
                                SettingService settingService,
                                InitParams initParams) {
    super(initParams);
    this.analyticsESClient = analyticsESClient;
    this.indexingService = indexingService;
    this.pageDAO = pageDAO;
    this.settingService = settingService;
    if (initParams != null) {
      if (initParams.getValueParam(SOURCE_ES_URL_PARAM) != null) {
        this.sourceESUrl = initParams.getValueParam(SOURCE_ES_URL_PARAM).getValue();
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

    try {
      if (analyticsESClient.sendIsIndexExistsRequest("wiki_v2")) {
        deleteAnalyticsIndices();
      }
      if (!reindexAllNotes())
        throw new IllegalStateException("ES Notes indexing failed");
    } catch (Exception e) {
      LOG.info("S Notes indexing proceeded successfully.");
    }
  }

  private void deleteAnalyticsIndices() throws ElasticClientException {
    LOG.info("START::Delete wiki index wiki_v2");
    analyticsESClient.sendHttpDeleteRequest("wiki_v2");
    LOG.info("END::Delete automatically created analytics index wiki_v2 successfully");
  }

  private boolean reindexAllNotes() {
    LOG.info("START::Indexing Notes");
    try {
      long startTime = System.currentTimeMillis();
      SettingValue<?> settingsValue = settingService.get(NOTES_ES_UPGRADE_CONTEXT,
                                                         NOTES_ES_UPGRADE_SCOPE,
                                                         NOTES_ES_UPGRADE_SETTINGS_KEY_NAME);
      int offset =
                 settingsValue == null || settingsValue.getValue() == null ? 0
                                                                           : Integer.valueOf(settingsValue.getValue().toString());
      int pageSize = 50;
      long idsSize = pageDAO.countAllIds();
      do {
        if (offset > idsSize)
          offset = (int) idsSize;
        for (Long id : pageDAO.findAllIds(offset, pageSize)) {
          indexingService.index(WikiPageIndexingServiceConnector.TYPE, String.valueOf(id));
        }
        offset += pageSize;
        settingService.set(NOTES_ES_UPGRADE_CONTEXT,
                           NOTES_ES_UPGRADE_SCOPE,
                           NOTES_ES_UPGRADE_SETTINGS_KEY_NAME,
                           SettingValue.create(String.valueOf(offset > idsSize ? idsSize : offset)));
      } while (offset < idsSize);
      LOG.info("END::All {} notes index created successfully. The operation took {} milliseconds.",
               idsSize,
               (System.currentTimeMillis() - startTime));
    } catch (Exception e) {
      LOG.error("Error while creating all notes indices", e);
      return false;
    }
    return true;
  }

}
