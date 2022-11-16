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
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AnalyticsChartTitlesUpgradePlugin extends UpgradeProductPlugin {
  private static final Log     LOG                        = ExoLogger.getExoLogger(AnalyticsChartTitlesUpgradePlugin.class);
  private final PortalContainer      container;
  private final EntityManagerService entityManagerService;
  private int                  pagesUpdatedCount;
  private Map<String , String> chartTitles = new HashMap<String ,String>();
  public AnalyticsChartTitlesUpgradePlugin(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams){
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
    this.chartTitles.put("Users count" , "analytics.usersCount");
    this.chartTitles.put("Spaces count" , "analytics.spacesCount");
    this.chartTitles.put("Activities" , "analytics.activitiesCount");
    this.chartTitles.put("Distinct logins" , "analytics.distinctLogins");

  }
  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return true;
  }
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    if ( chartTitles.isEmpty()) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    LOG.info("Start upgrade of chart old title to use new title");
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      String sqlString = "SELECT * FROM PORTAL_WINDOWS w WHERE w.CONTENT_ID = 'analytics/AnalyticsPortlet';";
      List<WindowEntity> pages =  entityManager.createNativeQuery(sqlString,WindowEntity.class).getResultList();
      if (!pages.isEmpty()) {
        for (WindowEntity page : pages) {
          for (Map.Entry<String, String> entry : chartTitles.entrySet()){
            String custstring = new String(page.getCustomization(),StandardCharsets.UTF_8) ;
            if (custstring.contains(entry.getKey())){
              custstring = custstring.replace(entry.getKey(), entry.getValue());
              byte[] custmByte = custstring.getBytes();
              String query = "UPDATE PORTAL_WINDOWS SET CUSTOMIZATION = :custmByte WHERE ID = :pageId ;";
              Query nativeQuery = entityManager.createNativeQuery(query).setParameter("custmByte",custmByte).setParameter("pageId",page.getId());
              this.pagesUpdatedCount = nativeQuery.executeUpdate();
              LOG.info("End upgrade of '{}' chart with title '{}' to use title '{}'. It took {} ms",
                       pagesUpdatedCount,
                       entry.getKey(),
                       entry.getValue(),
                       (System.currentTimeMillis() - startupTime));
            }
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
