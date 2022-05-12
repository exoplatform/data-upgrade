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

public class PublishedNewsDisplayedPropUpgrade extends UpgradeProductPlugin {
  private static final Log         LOG           = ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

  public static final MetadataType NEWS_TARGET_METADATA_TYPE = new MetadataType(4, "newsTarget");

  private EntityManagerService     entityManagerService;

  private NewsService              newsService;

  private MetadataService          metadataService;

  private PortalContainer          container;

  private int                      publishedNewsDisplayedPropCount = 0;

  public PublishedNewsDisplayedPropUpgrade(InitParams initParams,
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

  public int getPublishedNewsDisplayedPropCount() {
    return publishedNewsDisplayedPropCount;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of published news displayed property");

    boolean transactionStarted = false;
    RequestLifeCycle.begin(container);
    EntityManager entityManager = entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }
      List<Metadata> newsTargetMetadatas = metadataService.getMetadatas(NEWS_TARGET_METADATA_TYPE.getName(), 0);

      List<MetadataItemEntity> items = new ArrayList<>();
      for (Metadata newsTargetMetadata : newsTargetMetadatas) {
        String newsTargetsMetadataItemsQueryString = "SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID = '" + newsTargetMetadata.getId() + "'";
        Query newsTargetsMetadataItemsQuery = entityManager.createNativeQuery(newsTargetsMetadataItemsQueryString, MetadataItemEntity.class);
        List<MetadataItemEntity> newsTargetsMetadataItems = newsTargetsMetadataItemsQuery.getResultList();
        newsTargetsMetadataItems.forEach(item -> items.add(item));
      }
      News news = null;

      if (items.isEmpty()) {
        LOG.info("Metadata Items properties is empty");
      } else {
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
              publishedNewsDisplayedPropCount++;
            } else {
              sqlString3 = "INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('"+metadataItem.getId()+"', 'displayed', 'true');";
              publishedNewsDisplayedPropCount++;
            }
            Query nativeQuery1 = entityManager.createNativeQuery(sqlString3);
            nativeQuery1.executeUpdate();
          } catch (Exception e) {
            LOG.warn("Error while iterate metadata item {}", e);
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
    LOG.info("End upgrade of published news displayed property. It took {} ms", (System.currentTimeMillis() - startupTime));
  }
}
