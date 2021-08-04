package org.exoplatform.migration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;

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
import org.exoplatform.portal.mop.jdbc.dao.NodeDAO;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class NavigationNotesMigration extends UpgradeProductPlugin {

  private static final Log     LOG                        = ExoLogger.getExoLogger(NavigationNotesMigration.class);


  private static final String  OLD_NAVIGATION_NODE_NAME = "old.nav.name";

  private static final String  NEW_NAVIGATION_NODE_NAME = "new.nav.name";

  private final PortalContainer      container;

  private final EntityManagerService entityManagerService;

  private final Map<String, String>  nodesReferences      = new HashMap<>();

  private int                  pagesNodesCount;

  public NavigationNotesMigration(PortalContainer container, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.container = container;
    this.entityManagerService = entityManagerService;

    if (initParams.containsKey(OLD_NAVIGATION_NODE_NAME)) {
      String oldNavName = initParams.getValueParam(OLD_NAVIGATION_NODE_NAME).getValue();
      if (initParams.containsKey(NEW_NAVIGATION_NODE_NAME)) {
        String newNavName = initParams.getValueParam(NEW_NAVIGATION_NODE_NAME).getValue();
        if (StringUtils.isNotBlank(oldNavName) && StringUtils.isNotBlank(newNavName)) {
          nodesReferences.put(oldNavName, newNavName);
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
  public void processUpgrade(String oldVersion, String newVersion) {
    if (nodesReferences.isEmpty()) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", OLD_NAVIGATION_NODE_NAME);
      return;
    }

    long startupTime = System.currentTimeMillis();

    ExoContainerContext.setCurrentContainer(container);
    boolean transactionStarted = false;

    Set<Entry<String, String>> applicationReferencesEntrySet = nodesReferences.entrySet();
    for (Entry<String, String> applicationReference : applicationReferencesEntrySet) {
      String oldApplicationReference = applicationReference.getKey().trim();
      String newApplicationReference = applicationReference.getValue().trim();
      LOG.info("Start upgrade of navigation node with name '{}' to use name '{}'",
               oldApplicationReference,
               newApplicationReference);
      RequestLifeCycle.begin(this.entityManagerService);
      EntityManager entityManager = this.entityManagerService.getEntityManager();
      try {
        if (!entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().begin();
          transactionStarted = true;
        }

        String sqlString = "UPDATE PORTAL_NAVIGATION_NODES w SET w.NAME = '" + newApplicationReference
            + "' WHERE w.NAME = '" + oldApplicationReference + "' AND w.NODE_ID > 0;";
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
                oldApplicationReference,
                newApplicationReference,
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
  }

  public int getNodesUpdatedCount() {
    return pagesNodesCount;
  }
}
