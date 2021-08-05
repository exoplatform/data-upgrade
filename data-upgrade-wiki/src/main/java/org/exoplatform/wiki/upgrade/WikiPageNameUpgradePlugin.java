package org.exoplatform.wiki.upgrade;

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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class WikiPageNameUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(WikiPageNameUpgradePlugin.class);

  private static final String  OLD_NOTE_NAME = "old.note.name";

  private static final String  NEW_NOTE_NAME = "new.note.name";

  private static final String  NEW_NOTE_TITLE = "new.note.title";

  private  String  oldNoteName = "";

  private  String  newNoteName = "";

  private  String  newTitle = "";

  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private int                  pagesUpdatedCount;

  public WikiPageNameUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(OLD_NOTE_NAME)) {
      oldNoteName = initParams.getValueParam(OLD_NOTE_NAME).getValue();
    }
    if (initParams.containsKey(NEW_NOTE_NAME)) {
      newNoteName = initParams.getValueParam(NEW_NOTE_NAME).getValue();
    }
    if (initParams.containsKey(NEW_NOTE_TITLE)) {
      newTitle = initParams.getValueParam(NEW_NOTE_TITLE).getValue();
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
    if (StringUtils.isEmpty(oldNoteName)||StringUtils.isEmpty(newNoteName)||StringUtils.isEmpty(newTitle)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

      LOG.info("Start upgrade of notes with name '{}' to use name '{}'",
              oldNoteName,
              newNoteName);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE WIKI_PAGES w SET w.NAME = '" + newNoteName
                + "' , w.TITLE = '"+ newTitle +"' WHERE w.NAME = '" + oldNoteName + "' AND w.PAGE_ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesUpdatedCount = nativeQuery.executeUpdate();
        LOG.info("End upgrade of '{}' notes with name '{}' to use name '{}'. It took {} ms",
                pagesUpdatedCount,
                oldNoteName,
                newNoteName,
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
