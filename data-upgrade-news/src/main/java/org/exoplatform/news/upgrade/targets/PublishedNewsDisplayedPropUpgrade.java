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
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
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

public class PublishedNewsDisplayedPropUpgrade extends UpgradeProductPlugin {

  public static final String       STAGED_STATUS = "staged";

  private static final Log         LOG           = ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

  private EntityManagerService     entityManagerService;

  private NewsService              newsService;

  private MetadataService          metadataService;

  private PortalContainer          container;

  private int                      migratedPublishedNewsCount = 0;
  
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
    LOG.info("Start upgrade of published news displayed property");
    ExoContainerContext.setCurrentContainer(container);
    List<String> newsTargetMetadatas = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0).stream()
        .map(newsTargetMetadata -> String.valueOf(newsTargetMetadata.getId()))
        .collect(Collectors.toList());
    List<MetadataItemEntity> newsTargetsMetadataItems = new ArrayList<>();
    RequestLifeCycle.begin(entityManagerService);
    EntityManager entityManager = entityManagerService.getEntityManager();
    try {
      StringBuilder newsTargetsMetadataItemsQueryStringBuilder = new StringBuilder("SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID IN ");
      newsTargetsMetadataItemsQueryStringBuilder.append("(" + String.join(",", newsTargetMetadatas) + ")");
      Query newsTargetsMetadataItemsQuery = entityManager.createNativeQuery(newsTargetsMetadataItemsQueryStringBuilder.toString(), MetadataItemEntity.class);
      newsTargetsMetadataItems = newsTargetsMetadataItemsQuery.getResultList();
    } catch (Exception e) {
      throw new PersistenceException("Error when getting news target metadata items", e);
    } finally {
      RequestLifeCycle.end();
    }
    LOG.info("Total number of published news to be migrated: {}", newsTargetsMetadataItems.size());
    int notMigratedPublishedNewsCount = 0;
    for (List<MetadataItemEntity> newsTargetsMetadataItemsChunk : ListUtils.partition(newsTargetsMetadataItems, 10)) {
      try {
        int notMigratedPublishedNewsCountByTransaction = manageNewsTargetsMetadataItemsProps(newsTargetsMetadataItemsChunk, newsTargetsMetadataItems.size());
        notMigratedPublishedNewsCount += notMigratedPublishedNewsCountByTransaction;
      } catch (Exception e) {
        throw new PersistenceException("Error when managing news target metadata items props", e);
      }
    }
    LOG.info("End upgrade of published news displayed property, it tooks {} ms. Success: {}, Error: {}, Total: {}", (System.currentTimeMillis() - startupTime), migratedPublishedNewsCount, notMigratedPublishedNewsCount, newsTargetsMetadataItems.size());
  }
  
  @ExoTransactional
  public int manageNewsTargetsMetadataItemsProps(List<MetadataItemEntity> newsTargetsMetadataItems, int totalPublishedNewsToMigrate) throws Exception {
    int notMigratedPublishedNewsCount = 0;
    for (MetadataItemEntity newsTargetsMetadataItem : newsTargetsMetadataItems) {
      RequestLifeCycle.begin(entityManagerService);
      EntityManager entityManager = entityManagerService.getEntityManager();
      try {
        StringBuilder deleteNewsTargetsMetadataItemsPropsQueryStringBuilder = new StringBuilder("DELETE FROM SOC_METADATA_ITEMS_PROPERTIES WHERE METADATA_ITEM_ID = '");
        deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.append(newsTargetsMetadataItem.getId() + "'");
        deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.append(" AND (NAME = '" + STAGED_STATUS + "' OR NAME = '" + NewsUtils.DISPLAYED_STATUS + "')");
        Query deleteNewsTargetsMetadataItemsPropsQuery = entityManager.createNativeQuery(deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.toString(), MetadataItemEntity.class);
        deleteNewsTargetsMetadataItemsPropsQuery.executeUpdate();

        News news = newsService.getNewsById(newsTargetsMetadataItem.getObjectId(), false);
        boolean displayed = !news.isArchived() && !StringUtils.equals(news.getPublicationState(), STAGED_STATUS);
        StringBuilder insertNewsTargetsMetadataItemsPropQueryStringBuilder = new StringBuilder("INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('");
        insertNewsTargetsMetadataItemsPropQueryStringBuilder.append(newsTargetsMetadataItem.getId() + "', '");
        insertNewsTargetsMetadataItemsPropQueryStringBuilder.append(NewsUtils.DISPLAYED_STATUS + "', '");
        insertNewsTargetsMetadataItemsPropQueryStringBuilder.append(displayed + "')");
        Query insertNewsTargetsMetadataItemsPropQuery = entityManager.createNativeQuery(insertNewsTargetsMetadataItemsPropQueryStringBuilder.toString(), MetadataItemEntity.class);
        insertNewsTargetsMetadataItemsPropQuery.executeUpdate();
        migratedPublishedNewsCount++;
        LOG.info("{}/{} published news migrated", migratedPublishedNewsCount, totalPublishedNewsToMigrate);
      } catch (Exception e) {
        notMigratedPublishedNewsCount++;
        throw new PersistenceException("Error when managing news target metadata items props", e);
      } finally {
        RequestLifeCycle.end();
      }
    }
    //LOG.info("End upgrade of published news displayed property. Success: {}, Error: {}, Total: {}", migratedPublishedNewsCount, notMigratedPublishedNewsCount, newsTargetsMetadataItems.size());
    return notMigratedPublishedNewsCount;
  }
}