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

  public static final String       STAGED_STATUS = "staged";

  private static final Log         LOG           = ExoLogger.getLogger(PublishedNewsDisplayedPropUpgrade.class.getName());

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
    if (!entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().begin();
      transactionStarted = true;
    }
    List<Metadata> newsTargetMetadatas = metadataService.getMetadatas(NewsTargetingService.METADATA_TYPE.getName(), 0);
    List<MetadataItemEntity> newsTargetsMetadataItems = new ArrayList<>();
    for (Metadata newsTargetMetadata : newsTargetMetadatas) {
      StringBuilder newsTargetsMetadataItemsQueryStringBuilder = new StringBuilder("SELECT * FROM SOC_METADATA_ITEMS WHERE METADATA_ID = '");
      newsTargetsMetadataItemsQueryStringBuilder.append(newsTargetMetadata.getId() + "'");
      Query newsTargetsMetadataItemsQuery = entityManager.createNativeQuery(newsTargetsMetadataItemsQueryStringBuilder.toString(), MetadataItemEntity.class);
      newsTargetsMetadataItems.addAll(newsTargetsMetadataItemsQuery.getResultList());
    }
    for (MetadataItemEntity newsTargetsMetadataItem : newsTargetsMetadataItems) {
      StringBuilder deleteNewsTargetsMetadataItemsPropsQueryStringBuilder = new StringBuilder("DELETE FROM SOC_METADATA_ITEMS_PROPERTIES WHERE METADATA_ITEM_ID = '");
      deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.append(newsTargetsMetadataItem.getId() + "'");
      deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.append(" AND (NAME = '" + STAGED_STATUS + "' OR NAME = '" + NewsUtils.DISPLAYED_STATUS + "')");
      Query deleteNewsTargetsMetadataItemsPropsQuery = entityManager.createNativeQuery(deleteNewsTargetsMetadataItemsPropsQueryStringBuilder.toString(), MetadataItemEntity.class);
      deleteNewsTargetsMetadataItemsPropsQuery.executeUpdate();
      try {
        News news = newsService.getNewsById(newsTargetsMetadataItem.getObjectId(), false);
        boolean displayed = !news.isArchived() && !StringUtils.equals(news.getPublicationState(), STAGED_STATUS);
        StringBuilder insertNewsTargetsMetadataItemsPropsQueryStringBuilder = new StringBuilder("INSERT INTO SOC_METADATA_ITEMS_PROPERTIES(METADATA_ITEM_ID, NAME, VALUE) VALUES('");
        insertNewsTargetsMetadataItemsPropsQueryStringBuilder.append(newsTargetsMetadataItem.getId() + "', '");
        insertNewsTargetsMetadataItemsPropsQueryStringBuilder.append(NewsUtils.DISPLAYED_STATUS + "', '");
        insertNewsTargetsMetadataItemsPropsQueryStringBuilder.append(displayed + "')");
        Query insertNewsTargetsMetadataItemsPropsQuery = entityManager.createNativeQuery(insertNewsTargetsMetadataItemsPropsQueryStringBuilder.toString(), MetadataItemEntity.class);
        insertNewsTargetsMetadataItemsPropsQuery.executeUpdate();
        publishedNewsDisplayedPropCount++;
      } catch (Exception e) {
        if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
          entityManager.getTransaction().rollback();
        }
        LOG.error("Error while getting news by id {}", newsTargetsMetadataItem.getObjectId(), e);
      } finally {
        RequestLifeCycle.end();
      }
    }
    if (transactionStarted && entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().commit();
      entityManager.flush();
    }
    LOG.info("End upgrade of published news displayed property. It took {} ms", (System.currentTimeMillis() - startupTime));
  }
}
