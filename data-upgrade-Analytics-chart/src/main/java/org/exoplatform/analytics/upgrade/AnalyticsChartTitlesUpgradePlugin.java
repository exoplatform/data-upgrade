package org.exoplatform.analytics.upgrade;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.jdbc.entity.WindowEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsChartTitlesUpgradePlugin extends UpgradeProductPlugin {
  private static final Log     LOG                        = ExoLogger.getExoLogger(AnalyticsChartTitlesUpgradePlugin.class);
  private static final String OLD_USERSCOUNT_TITLE = "old.chart.UsersCount.title";
  private static final String OLD_SPACESCOUNT_TITLE = "old.chart.SpacesCount.title";
  private static final String OLD_ACTIVITIESCOUNT_TITLE = "old.chart.ActivitiesCount.title";
  private static final String OLD_DINSTINCTLOGINS_TITLE = "old.chart.DistinctLogins.title";
  private static final String NEW_SPACECOUNT_TITLE = "new.chart.SpacesCount.title";
  private static final String NEW_USERSCOUNT_TITLE = "new.chart.UsersCount.title";
  private static final String NEW_ACTIVITIESCOUNT_TITLE = "new.chart.ActivitiesCount.title";
  private static final String NEW_DISTINCTLOGINS_TITLE = "new.chart.DistinctLogins.title";
  private String oldUsersCountTitle = "";
  private String newUsersCountTitle = "";
  private String oldSpacesCountTitle = "";
  private String newSpacesCountTitle = "";
  private String oldActivitiesCountTitle = "";
  private String newActivitiesCountTitle = "";
  private String oldDistinctLoginsTitle = "";
  private String newDistinctLoginsTitle = "";
  private final String CONTENT_ID = "analytics/AnalyticsPortlet";
  private final PortalContainer      container;
  private final EntityManagerService entityManagerService;
  private int                  pagesUpdatedCount;
  public AnalyticsChartTitlesUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams){
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
    if (initParams.containsKey(OLD_ACTIVITIESCOUNT_TITLE)) {
      oldActivitiesCountTitle = initParams.getValueParam(OLD_ACTIVITIESCOUNT_TITLE).getValue();
    }
    if (initParams.containsKey(NEW_ACTIVITIESCOUNT_TITLE)) {
      newActivitiesCountTitle = initParams.getValueParam(NEW_ACTIVITIESCOUNT_TITLE).getValue();
    }
    if (initParams.containsKey(OLD_DINSTINCTLOGINS_TITLE)) {
      oldDistinctLoginsTitle = initParams.getValueParam(OLD_DINSTINCTLOGINS_TITLE).getValue();
    }
    if (initParams.containsKey(NEW_DISTINCTLOGINS_TITLE)) {
      newDistinctLoginsTitle = initParams.getValueParam(NEW_DISTINCTLOGINS_TITLE).getValue();
    }
    if (initParams.containsKey(OLD_SPACESCOUNT_TITLE)) {
      oldSpacesCountTitle = initParams.getValueParam(OLD_SPACESCOUNT_TITLE).getValue();
    }
    if (initParams.containsKey(NEW_SPACECOUNT_TITLE)) {
      newSpacesCountTitle = initParams.getValueParam(NEW_SPACECOUNT_TITLE).getValue();
    }
    if (initParams.containsKey(OLD_USERSCOUNT_TITLE)) {
      oldUsersCountTitle = initParams.getValueParam(OLD_USERSCOUNT_TITLE).getValue();
    }
    if (initParams.containsKey(NEW_USERSCOUNT_TITLE)) {
      newUsersCountTitle = initParams.getValueParam(NEW_USERSCOUNT_TITLE).getValue();
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
    if (StringUtils.isEmpty(oldActivitiesCountTitle) || StringUtils.isEmpty(newActivitiesCountTitle) || StringUtils.isEmpty(
        oldSpacesCountTitle) ||
        StringUtils.isEmpty(newSpacesCountTitle) || StringUtils.isEmpty(oldDistinctLoginsTitle) || StringUtils.isEmpty(
        newDistinctLoginsTitle) ||
        StringUtils.isEmpty(newUsersCountTitle) || StringUtils.isEmpty(oldUsersCountTitle)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    LOG.info("Start upgrade of chart with title '{}' to use title '{}'",
             oldUsersCountTitle,
             oldSpacesCountTitle,
             oldActivitiesCountTitle,
             oldDistinctLoginsTitle,
             newUsersCountTitle,
             newSpacesCountTitle,
             newActivitiesCountTitle,
             newDistinctLoginsTitle);
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      String sqlString = "SELECT w FROM PORTAL_WINDOWS WHERE w.CONTENT_ID ="+CONTENT_ID
          + "AND  w.CUSTOMIZATION IS NOT NULL";
      List<WindowEntity> pages = entityManager.createNativeQuery(sqlString).getResultList();
      if (!pages.isEmpty()) {
        for (WindowEntity page : pages) {
          if (page.getCustomization().toString().contains(OLD_USERSCOUNT_TITLE)){
            String Customisation = page.getCustomization().toString().replace(OLD_USERSCOUNT_TITLE,newUsersCountTitle);
            Byte custm = Byte.parseByte(Customisation) ;
            String query = "UPDATE PORTAL_WINDOWS SET CUSTOMIZATION ="+ custm + "WHERE ID ="+page.getId();
            Query nativeQuery = entityManager.createNativeQuery(query);
            this.pagesUpdatedCount = nativeQuery.executeUpdate();
            LOG.info("End upgrade of '{}' chart with title '{}' to use title '{}'. It took {} ms",
                     pagesUpdatedCount,
                     OLD_USERSCOUNT_TITLE,
                     newUsersCountTitle,
                     (System.currentTimeMillis() - startupTime));
          }
          if (page.getCustomization().toString().contains(OLD_ACTIVITIESCOUNT_TITLE)){
            String Customisation = page.getCustomization().toString().replace(OLD_ACTIVITIESCOUNT_TITLE,newActivitiesCountTitle);
            Byte custm = Byte.parseByte(Customisation) ;
            String query = "UPDATE PORTAL_WINDOWS SET CUSTOMIZATION ="+ custm + "WHERE ID ="+page.getId();
            Query nativeQuery = entityManager.createNativeQuery(query);
            this.pagesUpdatedCount = nativeQuery.executeUpdate();
            LOG.info("End upgrade of '{}' chart with title '{}' to use title '{}'. It took {} ms",
                     pagesUpdatedCount,
                     OLD_ACTIVITIESCOUNT_TITLE,
                     newActivitiesCountTitle,
                     (System.currentTimeMillis() - startupTime));
          }
          if (page.getCustomization().toString().contains(OLD_SPACESCOUNT_TITLE)){
            String Customisation = page.getCustomization().toString().replace(OLD_SPACESCOUNT_TITLE,newSpacesCountTitle);;
            Byte custm = Byte.parseByte(Customisation) ;
            String query = "UPDATE PORTAL_WINDOWS SET CUSTOMIZATION ="+ custm + "WHERE ID ="+page.getId();
            Query nativeQuery = entityManager.createNativeQuery(query);
            this.pagesUpdatedCount = nativeQuery.executeUpdate();
            LOG.info("End upgrade of '{}' chart with title '{}' to use title '{}'. It took {} ms",
                     pagesUpdatedCount,
                     OLD_SPACESCOUNT_TITLE,
                     newSpacesCountTitle,
                     (System.currentTimeMillis() - startupTime));
          }
          if (page.getCustomization().toString().contains(OLD_DINSTINCTLOGINS_TITLE)){
            String Customisation = page.getCustomization().toString().replace(OLD_DINSTINCTLOGINS_TITLE,newDistinctLoginsTitle);
            Byte custm = Byte.parseByte(Customisation) ;
            String query = "UPDATE PORTAL_WINDOWS SET CUSTOMIZATION ="+ custm + "WHERE ID ="+page.getId();
            Query nativeQuery = entityManager.createNativeQuery(query);
            this.pagesUpdatedCount = nativeQuery.executeUpdate();
            LOG.info("End upgrade of '{}' chart with title '{}' to use title '{}'. It took {} ms",
                     pagesUpdatedCount,
                     OLD_DINSTINCTLOGINS_TITLE,
                     newDistinctLoginsTitle,
                     (System.currentTimeMillis() - startupTime));
          }
        }
      }
      if (transactionStarted && entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().commit();
        entityManager.clear();
        entityManager.flush();
      }
      LOG.error("end upgrade off all  analytics chart title");
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
      LOG.error(e.getMessage());
    } finally {
      RequestLifeCycle.end();
    }
  }
  public int getPagesUpdatedCount() {
    return pagesUpdatedCount;
  }
}
