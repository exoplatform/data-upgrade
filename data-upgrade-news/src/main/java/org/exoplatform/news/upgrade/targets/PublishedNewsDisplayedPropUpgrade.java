/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.news.upgrade.targets;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.news.model.News;
import org.exoplatform.news.service.NewsService;
import org.exoplatform.news.service.NewsTargetingService;
import org.exoplatform.news.utils.NewsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.jpa.storage.entity.MetadataItemEntity;
import org.exoplatform.social.metadata.MetadataService;

public class PublishedNewsDisplayedPropUpgrade extends UpgradeProductPlugin {

  public static final String       STAGED_STATUS = "staged";

  private static final Log         LOG           = ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

  private EntityManagerService     entityManagerService;

  private NewsService              newsService;

  private MetadataService          metadataService;

  private PortalContainer          container;

  private int                      migratedPublishedNewsCount = 0;//Accessible by the test classes
  
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

  public int getMigratedPublishedNewsCount() {
    return migratedPublishedNewsCount;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start published news migration");
    List<MetadataItemEntity> newsTargetsMetadataItems = null;
    ExoContainerContext.setCurrentContainer(container);
    try {
      newsTargetsMetadataItems = getNewsTargetMetadataItems();
    } catch (Exception e) {
      throw new IllegalStateException ("Error when getting news target metadata items", e);
    } finally {
      ExoContainerContext.setCurrentContainer(null);
    }
    int totalPublishedNewsCount = newsTargetsMetadataItems.size();
    LOG.info("Total number of published news to be migrated: {}", totalPublishedNewsCount);
    int notMigratedPublishedNewsCount = 0;
    int processedPublishedNewsCount = 0;
    for (List<MetadataItemEntity> newsTargetsMetadataItemsChunk : ListUtils.partition(newsTargetsMetadataItems, 10)) {
      ExoContainerContext.setCurrentContainer(container);
      try {
        int notMigratedPublishedNewsCountByTransaction = manageNewsTargetsMetadataItemsProps(newsTargetsMetadataItemsChunk);
        int processedPublishedNewsCountByTransaction = newsTargetsMetadataItemsChunk.size();
        processedPublishedNewsCount += processedPublishedNewsCountByTransaction;
        migratedPublishedNewsCount += processedPublishedNewsCountByTransaction - notMigratedPublishedNewsCountByTransaction;
        notMigratedPublishedNewsCount += notMigratedPublishedNewsCountByTransaction; 
        LOG.info("Published news migration progress: processed={}/{} succeeded={} error={}", processedPublishedNewsCountByTransaction, processedPublishedNewsCount, migratedPublishedNewsCount, notMigratedPublishedNewsCount);
      } catch (Exception e) {
        throw new IllegalStateException ("Error when managing news target metadata items props", e);
      } finally {
        ExoContainerContext.setCurrentContainer(null);
      }
    }
    LOG.info("End published news migration: total={} succeeded={} error={}. It tooks {} ms.", totalPublishedNewsCount, migratedPublishedNewsCount, notMigratedPublishedNewsCount, (System.currentTimeMillis() - startupTime));
  }
  
  @ExoTransactional
  public List<MetadataItemEntity> getNewsTargetMetadataItems() throws IllegalStateException {
    List<String> newsTargetMetadatas = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0).stream()
        .map(newsTargetMetadata -> String.valueOf(newsTargetMetadata.getId()))
        .collect(Collectors.toList());
    List<MetadataItemEntity> newsTargetsMetadataItems = new ArrayList<>();
    EntityManager entityManager = entityManagerService.getEntityManager();
    try {
      Query getNewsTargetMetadataItemsQuery = entityManager.createNativeQuery("SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID IN :newsTargetMetadatas", MetadataItemEntity.class);
      getNewsTargetMetadataItemsQuery.setParameter("newsTargetMetadatas", newsTargetMetadatas);
      newsTargetsMetadataItems = getNewsTargetMetadataItemsQuery.getResultList();
    } catch (Exception e) {
      throw new IllegalStateException("Error when getting news target metadata items", e);
    }
    return newsTargetsMetadataItems;
  }
  
  @ExoTransactional
  public int manageNewsTargetsMetadataItemsProps(List<MetadataItemEntity> newsTargetsMetadataItems) throws IllegalStateException {
    int notMigratedPublishedNewsCount = 0;
    for (MetadataItemEntity newsTargetsMetadataItem : newsTargetsMetadataItems) {
      EntityManager entityManager = entityManagerService.getEntityManager();
      try {
        Query deleteNewsTargetMetadataItemsPropsQuery = entityManager.createNativeQuery("DELETE FROM SOC_METADATA_ITEMS_PROPERTIES WHERE METADATA_ITEM_ID = :newsTargetsMetadataItemId AND (NAME = :stagedStatus OR NAME = :displayedStatus)", MetadataItemEntity.class);
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("newsTargetsMetadataItemId", newsTargetsMetadataItem.getId());
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("stagedStatus", STAGED_STATUS);
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("displayedStatus", NewsUtils.DISPLAYED_STATUS);
        deleteNewsTargetMetadataItemsPropsQuery.executeUpdate();

        News news = newsService.getNewsById(newsTargetsMetadataItem.getObjectId(), false);
        boolean displayed = !news.isArchived() && !StringUtils.equals(news.getPublicationState(), STAGED_STATUS);
        Query insertNewsTargetMetadataItemsPropQuery = entityManager.createNativeQuery("INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES(:newsTargetsMetadataItemId, :displayedStatus, :displayed)", MetadataItemEntity.class);
        insertNewsTargetMetadataItemsPropQuery.setParameter("newsTargetsMetadataItemId", newsTargetsMetadataItem.getId());
        insertNewsTargetMetadataItemsPropQuery.setParameter("displayedStatus", NewsUtils.DISPLAYED_STATUS);
        insertNewsTargetMetadataItemsPropQuery.setParameter("displayed", String.valueOf(displayed));
        insertNewsTargetMetadataItemsPropQuery.executeUpdate();
      } catch (Exception e) {
        notMigratedPublishedNewsCount++;
        throw new IllegalStateException ("Error when managing news target metadata items props", e);
      }
    }
    return notMigratedPublishedNewsCount;
  }
}