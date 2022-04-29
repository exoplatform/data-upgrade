package org.exoplatform.news.upgrade.targets;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.model.News;
import org.exoplatform.news.service.NewsService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.jpa.storage.entity.MetadataItemEntity;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;
import org.exoplatform.social.metadata.model.MetadataType;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NewsArticlesStatusUpgrade extends UpgradeProductPlugin {
  private static final Log         LOG             = ExoLogger.getLogger(NewsArticlesStatusUpgrade.class.getName());

  public static final MetadataType METADATA_TYPE   = new MetadataType(4, "newsTarget");

  private EntityManagerService     entityManagerService;

  private boolean                  databaseUpdated = false;

  private NewsService              newsService;

  private MetadataService          metadataService;

  private PortalContainer          container;

  public NewsArticlesStatusUpgrade(InitParams initParams,
                                   EntityManagerService entityManagerService,
                                   NewsService newsService,
                                   MetadataService metadataService,
                                   PortalContainer container) {
    super(initParams);
    this.entityManagerService = entityManagerService;
    this.newsService = newsService;
    this.metadataService = metadataService;
    this.container = container;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of property item status of published news");

    boolean transactionStarted = false;
    PortalContainer container = PortalContainer.getInstance();
    RequestLifeCycle.begin(container);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      List<Metadata> metadataList = metadataService.getMetadatas(METADATA_TYPE.getName(), 0);

      List<MetadataItemEntity> metadataItems = new ArrayList<>();
      List<MetadataItemEntity> items = new ArrayList<>();
      if (!metadataList.isEmpty()) {
        for (Metadata metadataItem : metadataList) {
          String sqlString = "SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID = '" + metadataItem.getId() + "'";
          Query nativeQuery = entityManager.createNativeQuery(sqlString, MetadataItemEntity.class);
          metadataItems = nativeQuery.getResultList();
          metadataItems.forEach(item -> {
            items.add(item);
          });
        }
      }
      News news = null;

      if (items.isEmpty()) {
        LOG.info("Metadata Items properties is empty");
      } else {
        this.databaseUpdated = true;
        List<MetadataItemEntity> metadataItemsList = items.stream().distinct().collect(Collectors.toList());
        String sqlString2 = "DELETE FROM SOC_METADATA_ITEMS_PROPERTIES";
        Query nativeQuery2 = entityManager.createNativeQuery(sqlString2);
        nativeQuery2.executeUpdate();
        for (MetadataItemEntity metadataItem : metadataItemsList) {
          try {

            news = newsService.getNewsById(metadataItem.getObjectId(), false);
            String sqlString3 = null;
            if (news.isArchived() || StringUtils.equals(news.getPublicationState(), "staged")) {
              sqlString3 = "INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('"+metadataItem.getId()+"', 'displayed', 'false');";
            } else {
              sqlString3 = "INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('"+metadataItem.getId()+"', 'displayed', 'true');";
            }
            Query nativeQuery1 = entityManager.createNativeQuery(sqlString3);
            nativeQuery1.executeUpdate();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
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
    LOG.info("End upgrade of property item status of published news. It took {} ms", (System.currentTimeMillis() - startupTime));
  }
}
