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

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
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

  public static final String   INSERT_NEWS_TARGETS_METADATA_ITEMS_PROPS =
                                                                        "INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES(:newsTargetsMetadataItemId, :displayedStatus, :displayed)";

  public static final String   DELETE_NEWS_TARGETS_METADATA_ITEMS_PROPS =
                                                                        "DELETE FROM SOC_METADATA_ITEMS_PROPERTIES WHERE METADATA_ITEM_ID = :newsTargetsMetadataItemId AND (NAME = :stagedStatus OR NAME = :displayedStatus)";

  public static final String   GET_NEWS_TARGET_METADATA_ITEMS           =
                                                              "SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID IN :newsTargetMetadatas";

  public static final String   STAGED_STATUS              = "staged";

  private static final Log     LOG                        =
                                   ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

  private EntityManagerService entityManagerService;

  private NewsService          newsService;

  private MetadataService      metadataService;

  private int                  migratedPublishedNewsCount = 0;                                            // Accessible
                                                                                                          // by
                                                                                                          // the
                                                                                                          // test
                                                                                                          // classes

  public PublishedNewsDisplayedPropUpgrade(InitParams initParams,
                                           EntityManagerService entityManagerService,
                                           NewsService newsService,
                                           MetadataService metadataService) {
    super(initParams);
    this.entityManagerService = entityManagerService;
    this.newsService = newsService;
    this.metadataService = metadataService;
  }

  public int getMigratedPublishedNewsCount() {
    return migratedPublishedNewsCount;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start published news migration");
    List<MetadataItemEntity> newsTargetsMetadataItems = getNewsTargetMetadataItems();

    int totalPublishedNewsCount = newsTargetsMetadataItems.size();
    LOG.info("Total number of published news to be migrated: {}", totalPublishedNewsCount);
    int notMigratedPublishedNewsCount = 0;
    int processedPublishedNewsCount = 0;
    for (List<MetadataItemEntity> newsTargetsMetadataItemsChunk : ListUtils.partition(newsTargetsMetadataItems, 10)) {
      int notMigratedPublishedNewsCountByTransaction = manageNewsTargetsMetadataItemsProps(newsTargetsMetadataItemsChunk);
      int processedPublishedNewsCountByTransaction = newsTargetsMetadataItemsChunk.size();
      processedPublishedNewsCount += processedPublishedNewsCountByTransaction;
      migratedPublishedNewsCount += processedPublishedNewsCountByTransaction - notMigratedPublishedNewsCountByTransaction;
      notMigratedPublishedNewsCount += notMigratedPublishedNewsCountByTransaction;
      LOG.info("Published news migration progress: processed={}/{} succeeded={} error={}",
               processedPublishedNewsCount,
               totalPublishedNewsCount,
               migratedPublishedNewsCount,
               notMigratedPublishedNewsCount);
    }
    if (notMigratedPublishedNewsCount == 0) {
      LOG.info("End published news successful migration: total={} succeeded={} error={}. It tooks {} ms.",
               totalPublishedNewsCount,
               migratedPublishedNewsCount,
               notMigratedPublishedNewsCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End published news migration with some errors: total={} succeeded={} error={}. It tooks {} ms."
          + "The not migrated items will be processed again next startup.",
               totalPublishedNewsCount,
               migratedPublishedNewsCount,
               notMigratedPublishedNewsCount,
               (System.currentTimeMillis() - startupTime));
      throw new IllegalStateException("Some news items wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  @SuppressWarnings("unchecked")
  @ExoTransactional
  public List<MetadataItemEntity> getNewsTargetMetadataItems() {
    List<Long> newsTargetMetadatas = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0)
                                                    .stream()
                                                    .map(Metadata::getId)
                                                    .toList();
    EntityManager entityManager = entityManagerService.getEntityManager();
    Query getNewsTargetMetadataItemsQuery =
                                          entityManager.createNativeQuery(GET_NEWS_TARGET_METADATA_ITEMS,
                                                                          MetadataItemEntity.class);
    getNewsTargetMetadataItemsQuery.setParameter("newsTargetMetadatas", newsTargetMetadatas);
    return getNewsTargetMetadataItemsQuery.getResultList();
  }

  @ExoTransactional
  public int manageNewsTargetsMetadataItemsProps(List<MetadataItemEntity> newsTargetsMetadataItems) {
    int notMigratedPublishedNewsCount = 0;
    for (MetadataItemEntity newsTargetsMetadataItem : newsTargetsMetadataItems) {
      EntityManager entityManager = entityManagerService.getEntityManager();
      try {
        Query deleteNewsTargetMetadataItemsPropsQuery =
                                                      entityManager.createNativeQuery(DELETE_NEWS_TARGETS_METADATA_ITEMS_PROPS,
                                                                                      MetadataItemEntity.class);
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("newsTargetsMetadataItemId", newsTargetsMetadataItem.getId());
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("stagedStatus", STAGED_STATUS);
        deleteNewsTargetMetadataItemsPropsQuery.setParameter("displayedStatus", NewsUtils.DISPLAYED_STATUS);
        deleteNewsTargetMetadataItemsPropsQuery.executeUpdate();

        News news = newsService.getNewsById(newsTargetsMetadataItem.getObjectId(), false);
        boolean displayed = !news.isArchived() && !StringUtils.equals(news.getPublicationState(), STAGED_STATUS);
        Query insertNewsTargetMetadataItemsPropQuery =
                                                     entityManager.createNativeQuery(INSERT_NEWS_TARGETS_METADATA_ITEMS_PROPS,
                                                                                     MetadataItemEntity.class);
        insertNewsTargetMetadataItemsPropQuery.setParameter("newsTargetsMetadataItemId", newsTargetsMetadataItem.getId());
        insertNewsTargetMetadataItemsPropQuery.setParameter("displayedStatus", NewsUtils.DISPLAYED_STATUS);
        insertNewsTargetMetadataItemsPropQuery.setParameter("displayed", String.valueOf(displayed));
        insertNewsTargetMetadataItemsPropQuery.executeUpdate();
      } catch (Exception e) {
        LOG.warn("Error migrating metadata item with id {}. Continue to migrate other items", newsTargetsMetadataItem.getId(), e);
        notMigratedPublishedNewsCount++;
      }
    }
    return notMigratedPublishedNewsCount;
  }
}
