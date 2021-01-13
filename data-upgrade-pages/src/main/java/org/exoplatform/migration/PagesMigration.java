package org.exoplatform.migration;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class PagesMigration extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(PagesMigration.class);

  private static final String  OLD_APPLICATION_CONTENT_ID = "old.application.contentId";

  private static final String  NEW_APPLICATION_CONTENT_ID = "new.application.contentId";

  private PortalContainer      container;

  private EntityManagerService entityManagerService;

  private String               oldApplicationReference;

  private String               newApplicationReference;

  private int                  pagesUpdatedCount;

  public PagesMigration(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(OLD_APPLICATION_CONTENT_ID)) {
      oldApplicationReference = initParams.getValueParam(OLD_APPLICATION_CONTENT_ID).getValue();
    }
    if (initParams.containsKey(NEW_APPLICATION_CONTENT_ID)) {
      newApplicationReference = initParams.getValueParam(NEW_APPLICATION_CONTENT_ID).getValue();
    }
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    if (StringUtils.isBlank(oldApplicationReference)) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", OLD_APPLICATION_CONTENT_ID);
      return;
    }
    if (StringUtils.isBlank(newApplicationReference)) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", NEW_APPLICATION_CONTENT_ID);
      return;
    }

    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    boolean transactionStarted = false;

    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of pages with application references '{}' to use application '{}'",
             oldApplicationReference,
             newApplicationReference);
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }

      String sqlString = "UPDATE PORTAL_WINDOWS w SET w.CONTENT_ID = '" + newApplicationReference
          + "' WHERE w.CONTENT_ID = '" + oldApplicationReference + "' AND w.ID > 0;";
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

  public int getPagesUpdatedCount() {
    return pagesUpdatedCount;
  }
}
