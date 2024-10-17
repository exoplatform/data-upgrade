/*
 * Copyright (C) 2024 eXo Platform SAS.
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
package org.exoplatform.news.upgrade;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.attachments.storage.AttachmentStorage;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ArticleAttachmentsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log     LOG                           = ExoLogger.getLogger(ArticleAttachmentsUpgradePlugin.class);

  private SettingService       settingService;

  private NoteService          noteService;

  private EntityManagerService entityManagerService;

  private AttachmentStorage    attachmentStorage;

  private final PortalContainer container;

  private static final String  ARTICLES_UPGRADE_EXECUTED_KEY = "articlesUpgradeExecuted";

  private static final String  ARTICLES_UPGRADE_PLUGIN_NAME  = "NewsArticlesUpgradePlugin";

  public ArticleAttachmentsUpgradePlugin(InitParams initParams,
                                         EntityManagerService entityManagerService,
                                         SettingService settingService,
                                         NoteService noteService,
                                         AttachmentStorage attachmentStorage, PortalContainer container) {
    super(initParams);
    this.settingService = settingService;
    this.entityManagerService = entityManagerService;
    this.noteService = noteService;
    this.attachmentStorage = attachmentStorage;
    this.container = container;
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousGroupVersion) {
    SettingValue<?> settingValue = settingService.get(Context.GLOBAL.id(ARTICLES_UPGRADE_PLUGIN_NAME),
                                                      Scope.APPLICATION.id(ARTICLES_UPGRADE_PLUGIN_NAME),
                                                      ARTICLES_UPGRADE_EXECUTED_KEY);
    if (settingValue == null || settingValue.getValue().equals("false")) {
      return false;
    }
    return super.shouldProceedToUpgrade(newVersion, previousGroupVersion);
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    ExoContainerContext.setCurrentContainer(container);
    long startupTime = System.currentTimeMillis();
    int attachmentCount = 0;
    int articleCount = 0;
    boolean transactionStarted = false;
    RequestLifeCycle.begin(this.entityManagerService);
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }

      List<Object[]> results = getAttachments(entityManager);
      if (results.isEmpty()) {
        return;
      }
      LOG.info("Start updating article attachments");
      for (Object[] result : results) {
        String versionId = (String) result[0];
        String originalVersionId = versionId;
        String propertyValue = (String) result[1]; // get the value
        PageVersion pageVersion = noteService.getPageVersionById(Long.valueOf(versionId));
        if (pageVersion != null && pageVersion.getParent() != null && StringUtils.isNotEmpty(pageVersion.getParent().getId())) {
          PageVersion latestVersion =
                                    noteService.getPublishedVersionByPageIdAndLang(Long.valueOf(pageVersion.getParent().getId()),
                                                                                   null);
          versionId = latestVersion.getId();
        }
        String[] attachmentIds = propertyValue.split(";");
        attachmentCount += linkAttachmentsToEntity(versionId, attachmentIds);
        articleCount += 1;
        LOG.info("{} attachments linked to {} articles", attachmentCount, articleCount);

        removeAttachmentProperty(originalVersionId, entityManager, transactionStarted);
      }
      if (transactionStarted && entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().commit();
      }
      LOG.info("Updating article attachments done it took {} ms", System.currentTimeMillis() - startupTime);
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
      LOG.error("Error when processing article attachments upgrade plugin", e);
    } finally {
      RequestLifeCycle.end();
    }
  }

  private int linkAttachmentsToEntity(String articleId, String[] attachmentIds) {
    AtomicInteger linkedAttachments = new AtomicInteger(0);

    Arrays.stream(attachmentIds).forEach(id -> {
      try {
        // Link the attachment to the entity
        this.attachmentStorage.linkAttachmentToEntity(Long.parseLong(articleId), "WIKI_PAGE_VERSIONS", id);
        linkedAttachments.incrementAndGet();
      } catch (Exception e) {
        LOG.error("Error when linking attachment with id {} to entity with id {}", id, articleId, e);
      }
    });

    return linkedAttachments.get();
  }

  private void removeAttachmentProperty(String articleId, EntityManager entityManager, boolean transactionStarted) {

    String deleteQuery = "DELETE FROM SOC_METADATA_ITEMS_PROPERTIES "
        + "WHERE metadata_item_id IN (SELECT metadata_item_id FROM SOC_METADATA_ITEMS WHERE object_id = '" + articleId + "')"
        + " AND name = 'attachmentsIds'";

    Query query = entityManager.createNativeQuery(deleteQuery);
    query.executeUpdate();
  }

  private List<Object[]> getAttachments(EntityManager entityManager) {
    String selectQuery = "SELECT mi.object_id, p.value " + "FROM SOC_METADATA_ITEMS mi " + "JOIN SOC_METADATA_ITEMS_PROPERTIES p "
        + "ON mi.metadata_item_id = p.metadata_item_id " + "WHERE mi.object_type = 'newsPageVersion' "
        + "AND p.name = 'attachmentsIds' " + "AND mi.object_id IS NOT NULL " + "AND mi.object_id != '' "
        + "AND p.value IS NOT NULL " + "AND p.value != '';";
    Query nativeQuery = entityManager.createNativeQuery(selectQuery);
    return nativeQuery.getResultList();
  }
}
