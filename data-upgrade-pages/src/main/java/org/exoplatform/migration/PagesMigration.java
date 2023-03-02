package org.exoplatform.migration;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.mop.storage.cache.CacheLayoutStorage;
import org.exoplatform.portal.mop.storage.cache.CachePageStorage;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class PagesMigration extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(PagesMigration.class);

  private static final String  APPLICATION_CONTENT_IDS    = "application.contentIds";

  private static final String  OLD_APPLICATION_CONTENT_ID = "old.application.contentId";

  private static final String  NEW_APPLICATION_CONTENT_ID = "new.application.contentId";

  private PortalContainer      container;

  private EntityManagerService entityManagerService;

  private CacheService         cacheService;

  private Map<String, String>  applicationReferences      = new HashMap<>();

  private int                  pagesUpdatedCount;

  public PagesMigration(PortalContainer container,
                        EntityManagerService entityManagerService,
                        CacheService cacheService,
                        InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
    this.cacheService = cacheService;

    if (initParams.containsKey(OLD_APPLICATION_CONTENT_ID)) {
      String oldApplicationReference = initParams.getValueParam(OLD_APPLICATION_CONTENT_ID).getValue();
      if (initParams.containsKey(NEW_APPLICATION_CONTENT_ID)) {
        String newApplicationReference = initParams.getValueParam(NEW_APPLICATION_CONTENT_ID).getValue();
        if (StringUtils.isNotBlank(oldApplicationReference) && StringUtils.isNotBlank(newApplicationReference)) {
          applicationReferences.put(oldApplicationReference, newApplicationReference);
        }
      }
    } else if (initParams.containsKey(APPLICATION_CONTENT_IDS)) {
      List<String> values = initParams.getValuesParam(APPLICATION_CONTENT_IDS).getValues();
      for (String value : values) {
        String[] parts = value.split(":");
        String oldApplicationReference = parts[0];
        String newApplicationReference = parts[1];
        if (StringUtils.isNotBlank(oldApplicationReference) && StringUtils.isNotBlank(newApplicationReference)) {
          applicationReferences.put(oldApplicationReference, newApplicationReference);
        }
      }
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) { // NOSONAR
    if (applicationReferences.isEmpty()) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", OLD_APPLICATION_CONTENT_ID);
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    Set<Entry<String, String>> applicationReferencesEntrySet = applicationReferences.entrySet();
    for (Entry<String, String> applicationReference : applicationReferencesEntrySet) {
      String oldApplicationReference = applicationReference.getKey().trim();
      String newApplicationReference = applicationReference.getValue().trim();
      LOG.info("Start upgrade of pages with application references '{}' to use application '{}'",
               oldApplicationReference,
               newApplicationReference);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_WINDOWS  SET CONTENT_ID = '" + newApplicationReference
            + "' WHERE CONTENT_ID = '" + oldApplicationReference + "' AND ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesUpdatedCount = nativeQuery.executeUpdate();
        LOG.info("End upgrade of '{}' pages with application references '{}' to use application '{}'. It took {} ms",
                 pagesUpdatedCount,
                 oldApplicationReference,
                 newApplicationReference,
                 (System.currentTimeMillis() - startupTime));
        if (transactionStarted && entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().commit();
          entityManager.flush();
        }
      } catch (Exception e) {
        if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
          entityManager.getTransaction().rollback();
        }
      } finally {
        RequestLifeCycle.end();
      }
    }
    clearPagesCache();
  }

  public int getPagesUpdatedCount() {
    return pagesUpdatedCount;
  }

  private void clearPagesCache() {
    if (pagesUpdatedCount > 0) {
      ExoCache<? extends Serializable, ?> pagesCache = this.cacheService.getAllCacheInstances()
                                                                        .stream()
                                                                        .filter(cache -> CachePageStorage.PAGE_CACHE_NAME.equals(cache.getName()))
                                                                        .findFirst()
                                                                        .orElse(null);
      if (pagesCache != null) {
        pagesCache.clearCache();
      }
      ExoCache<? extends Serializable, ?> preferencesCache = this.cacheService.getAllCacheInstances()
                                                                              .stream()
                                                                              .filter(cache -> CacheLayoutStorage.PORTLET_PREFERENCES_CACHE_NAME.equals(cache.getName()))
                                                                              .findFirst()
                                                                              .orElse(null);
      if (preferencesCache != null) {
        preferencesCache.clearCache();
      }
    }
  }
}
