package org.exoplatform.application.upgrade;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class AppRegistryUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(AppRegistryUpgradePlugin.class);




  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private int                  pagesUpdatedCount;

  private static final String  OLD_CONTENT_ID = "old.content.id";

  private static final String  NEW_DESCRIPTION = "new.description";

  private static final String  NEW_DISPLAY_NAME = "new.display.name";


  private static final String  NEW_APP_NAME = "new.app.name";


  private static final String  NEW_CONTENT_ID = "new.content.id";

  private  String              oldContentId;
  private  String              newDisplayName;
  private  String              newAppName;
  private  String              newContentId;
  private  String              newDescription;



  public AppRegistryUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;


    if (initParams.containsKey(OLD_CONTENT_ID)) {
      oldContentId = initParams.getValueParam(OLD_CONTENT_ID).getValue();
    }
    if (initParams.containsKey(NEW_DISPLAY_NAME)) {
      newDisplayName = initParams.getValueParam(NEW_DISPLAY_NAME).getValue();
    }
    if (initParams.containsKey(NEW_DESCRIPTION)) {
      newDescription = initParams.getValueParam(NEW_DESCRIPTION).getValue();
    }

    if (initParams.containsKey(NEW_APP_NAME)) {
      newAppName = initParams.getValueParam(NEW_APP_NAME).getValue();
    }

    if (initParams.containsKey(NEW_CONTENT_ID)) {
      newContentId = initParams.getValueParam(NEW_CONTENT_ID).getValue();
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
  public void processUpgrade(String oldVersion, String newVersion) {
    if (StringUtils.isEmpty(oldContentId)||StringUtils.isEmpty(newAppName)||StringUtils.isEmpty(newDisplayName)||StringUtils.isEmpty(newDescription)||StringUtils.isEmpty(newContentId)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }
    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

      LOG.info("Start upgrade of app with content id {} in registry to use {}", oldContentId,newContentId);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_APPLICATIONS SET DISPLAY_NAME = '"+newDisplayName+"' , DESCRIPTION = '"+newDescription+"' , APP_NAME = '"+newAppName+"', CONTENT_ID = '"+newContentId+"' WHERE CONTENT_ID = '"+oldContentId+"'  AND ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesUpdatedCount = nativeQuery.executeUpdate();
        LOG.info("End upgrade of '{}' apps with content id {} in registry to use {}. It took {} ms",
                oldContentId,
                newContentId,
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
