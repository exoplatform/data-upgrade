package org.exoplatform.news.upgrade.targets;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.model.News;
import org.exoplatform.news.service.NewsService;
import org.exoplatform.news.service.NewsTargetingService;
import org.exoplatform.news.utils.NewsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.jpa.storage.entity.MetadataItemEntity;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.Metadata;

public class PublishedNewsDisplayedPropUpgrade extends UpgradeProductPlugin {
  private static final Log         LOG           = ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

  private static final String      STAGED_STATUS = "staged";

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
      List<Metadata> newsTargetMetadatas = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0);

      List<MetadataItemEntity> newsTargetsMetadataItems = new ArrayList<>();
      for (Metadata newsTargetMetadata : newsTargetMetadatas) {
        String newsTargetsMetadataItemsQueryString = "SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID = '" + newsTargetMetadata.getId() + "'";
        Query newsTargetsMetadataItemsQuery = entityManager.createNativeQuery(newsTargetsMetadataItemsQueryString, MetadataItemEntity.class);
        newsTargetsMetadataItems.addAll(newsTargetsMetadataItemsQuery.getResultList());
      }
      News news = null;

      for (MetadataItemEntity newsTargetsMetadataItem : newsTargetsMetadataItems) {
        try {
          String deleteNewsTargetsMetadataItemsPropsQueryString = "DELETE FROM SOC_METADATA_ITEMS_PROPERTIES WHERE METADATA_ITEM_ID = '" + newsTargetsMetadataItem.getId() + "' AND (NAME = '" + STAGED_STATUS + "' OR NAME = '" + NewsUtils.DISPLAYED_STATUS + "')";
          Query deleteNewsTargetsMetadataItemsPropsQuery = entityManager.createNativeQuery(deleteNewsTargetsMetadataItemsPropsQueryString);
          deleteNewsTargetsMetadataItemsPropsQuery.executeUpdate();
          news = newsService.getNewsById(newsTargetsMetadataItem.getObjectId(), false);
          boolean displayed = !(news.isArchived() || StringUtils.equals(news.getPublicationState(), STAGED_STATUS));
          String insertNewsTargetsMetadataItemsPropsQueryString = "INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('"+ newsTargetsMetadataItem.getId() + "', '" + NewsUtils.DISPLAYED_STATUS + "', '" + displayed + "');";
          publishedNewsDisplayedPropCount++;
          Query insertNewsTargetsMetadataItemsPropsQuery = entityManager.createNativeQuery(insertNewsTargetsMetadataItemsPropsQueryString);
          insertNewsTargetsMetadataItemsPropsQuery.executeUpdate();
        } catch (Exception e) {
          LOG.warn("Error while iterate metadata item {}", e);
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
