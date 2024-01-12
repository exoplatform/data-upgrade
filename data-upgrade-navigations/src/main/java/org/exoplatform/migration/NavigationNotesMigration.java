package org.exoplatform.migration;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.jdbc.entity.NodeEntity;
import org.exoplatform.portal.mop.dao.NodeDAO;
import org.exoplatform.portal.mop.storage.cache.CacheDescriptionStorage;
import org.exoplatform.portal.mop.storage.cache.CacheNavigationStorage;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class NavigationNotesMigration extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(NavigationNotesMigration.class);


  private static final String  OLD_NAVIGATION_NODE_NAME = "old.nav.name";

  private static final String  NEW_NAVIGATION_NODE_NAME = "new.nav.name";

  private static final String  NEW_NAVIGATION_NODE_LABEL = "new.nav.label";

  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private CacheService         cacheService;

  private int                  pagesNodesCount;

  private  String              oldNavName;
  private  String              newNavName;
  private  String              newNavLabel;

  public NavigationNotesMigration(PortalContainer container,
                                  EntityManagerService entityManagerService,
                                  CacheService cacheService,
                                  InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;
    this.cacheService = cacheService;

    if (initParams.containsKey(OLD_NAVIGATION_NODE_NAME)) {
      oldNavName = initParams.getValueParam(OLD_NAVIGATION_NODE_NAME).getValue();
    }
    if (initParams.containsKey(NEW_NAVIGATION_NODE_NAME)) {
      newNavName = initParams.getValueParam(NEW_NAVIGATION_NODE_NAME).getValue();
    }

    if (initParams.containsKey(NEW_NAVIGATION_NODE_LABEL)) {
      newNavLabel = initParams.getValueParam(NEW_NAVIGATION_NODE_LABEL).getValue();
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
    if (StringUtils.isEmpty(oldNavName)||StringUtils.isEmpty(newNavName)||StringUtils.isEmpty(newNavLabel)) {
      LOG.error("Couldn't process upgrade, all parameters are mandatory");
      return;
    }
    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

      LOG.info("Start upgrade of navigation node with name '{}' to use name '{}'",
               oldNavName,
               newNavName);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_NAVIGATION_NODES SET NAME = '" + newNavName
            + "' ,LABEL = '" + newNavLabel
            + "' WHERE NAME = '" + oldNavName + "' AND NODE_ID > 0;";
        Query nativeQuery = entityManager.createNativeQuery(sqlString);
        this.pagesNodesCount = nativeQuery.executeUpdate();

        String sqlString1 ="SELECT * FROM PORTAL_NAVIGATION_NODES as T\n" +
                "              Where Exists    (\n" +
                "                Select 1\n" +
                "                From PORTAL_NAVIGATION_NODES As T2\n" +
                "                Where T2.PARENT_ID =  T.PARENT_ID and T2.NAME =  T.NAME\n" +
                "                    And T2.NODE_ID <> T.NODE_ID\n" +
                "                )";

        Query nativeQuery1 = entityManager.createNativeQuery(sqlString1,NodeEntity.class);
        List<NodeEntity> resultList = nativeQuery1.getResultList();
        NodeDAO nodeDAO = CommonsUtils.getService(NodeDAO.class);
        LOG.info(resultList);
        List<Long> parentIDList = resultList.stream().map(nodeEntity ->  nodeEntity.getParent().getId()).distinct().collect(Collectors.toList());
        for(Long parentID : parentIDList){
          List<NodeEntity> nodesListWithSameParent = resultList.stream().filter(c -> c.getParent().getId() == parentID).collect(Collectors.toList());
          for(int i = 1; i < nodesListWithSameParent.size(); i++){
            nodesListWithSameParent.get(i).setName(nodesListWithSameParent.get(i).getName()+"_"+i);
          }
          nodeDAO.updateAll(nodesListWithSameParent);
        }
        LOG.info("End upgrade of '{}' navigation node with name '{}' to use name '{}'. It took {} ms",
                pagesNodesCount,
                oldNavName,
                newNavName,
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
      clearNavigationCache();
  }

  public int getNodesUpdatedCount() {
    return pagesNodesCount;
  }

  private void clearNavigationCache() {
    if (pagesNodesCount > 0) {
      ExoCache<? extends Serializable, ?> navigationCache = this.cacheService.getAllCacheInstances()
                                                                             .stream()
                                                                             .filter(cache -> CacheNavigationStorage.NAVIGATION_CACHE_NAME.equals(cache.getName()))
                                                                             .findFirst()
                                                                             .orElse(null);
      if (navigationCache != null) {
        navigationCache.clearCache();
      }
      ExoCache<? extends Serializable, ?> descriptionCache = this.cacheService.getAllCacheInstances()
                                                                              .stream()
                                                                              .filter(cache -> CacheDescriptionStorage.DESCRIPTION_CACHE_NAME.equals(cache.getName()))
                                                                              .findFirst()
                                                                              .orElse(null);
      if (descriptionCache != null) {
        descriptionCache.clearCache();
      }
    }
  }
}
