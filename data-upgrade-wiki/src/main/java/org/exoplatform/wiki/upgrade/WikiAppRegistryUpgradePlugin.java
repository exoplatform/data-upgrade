package org.exoplatform.wiki.upgrade;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class WikiAppRegistryUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(WikiAppRegistryUpgradePlugin.class);




  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private int                  pagesUpdatedCount;

  public WikiAppRegistryUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
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

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

      LOG.info("Start upgrade of wiki app name in registry to use notes");
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_APPLICATIONS w SET w.DISPLAY_NAME = 'Notes' , w.DESCRIPTION = 'Notes Portlet' , w.APP_NAME = 'Notes', w.CONTENT_ID = 'notes/Notes' WHERE w.CONTENT_ID = 'wiki/WikiPortlet'  AND w.ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesUpdatedCount = nativeQuery.executeUpdate();
        LOG.info("End upgrade of '{}'  wiki app name in registry to use notes. It took {} ms",
                pagesUpdatedCount,
                (System.currentTimeMillis() - startupTime));
        if (transactionStarted && entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().commit();
          entityManager.clear();
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
