/*
 * Copyright (C) 2025 eXo Platform SAS.
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

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.commons.collections4.ListUtils;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataType;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.news.model.News;
import io.meeds.news.model.NewsPageObject;
import io.meeds.news.service.NewsService;
import io.meeds.news.service.impl.NewsServiceImpl;
import io.meeds.news.utils.NewsUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class ContentDraftArticlesUpgrade extends UpgradeProductPlugin {

  private static final Log         LOG                                         =
                                       ExoLogger.getLogger(ContentDraftArticlesUpgrade.class.getName());

  private ActivityManager          activityManager;

  private IdentityManager          identityManager;

  private NoteService              noteService;

  private NewsService              newsService;

  private SpaceService             spaceService;

  private SettingService           settingService;

  private MetadataService          metadataService;

  private EntityManagerService     entityManagerService;

  private int                      migratedDraftArticlesCount                  = 0;

  public static final MetadataType NEWS_METADATA_TYPE                          = new MetadataType(1000, "news");

  public static final String       NEWS_METADATA_NAME                          = "news";

  private static final String      PLUGIN_NAME                                 = "ContentDraftArticlesUpgrade";

  private static final String      PLUGIN_EXECUTED_KEY                         = "contentDraftArticlesUpgradeExecuted";

  private static final String      CONTENT_INCONSISTENT_DRAFT_ARTICLES_UPGRADE = "content.inconsistent.draft.articles.upgrade";

  private String                   contentInconsistentDraftArticlesUpgrade;

  private boolean                  upgradeFailed                               = false;

  public ContentDraftArticlesUpgrade(InitParams initParams,
                                     ActivityManager activityManager,
                                     IdentityManager identityManager,
                                     NoteService noteService,
                                     NewsService newsService,
                                     SpaceService spaceService,
                                     SettingService settingService,
                                     MetadataService metadataService,
                                     EntityManagerService entityManagerService) {
    super(initParams);
    if (initParams.containsKey(CONTENT_INCONSISTENT_DRAFT_ARTICLES_UPGRADE)) {
      contentInconsistentDraftArticlesUpgrade = initParams.getValueParam(CONTENT_INCONSISTENT_DRAFT_ARTICLES_UPGRADE).getValue();
    }
    this.activityManager = activityManager;
    this.identityManager = identityManager;
    this.noteService = noteService;
    this.newsService = newsService;
    this.spaceService = spaceService;
    this.settingService = settingService;
    this.metadataService = metadataService;
    this.entityManagerService = entityManagerService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of draft articles");
    int notMigratedDraftArticlesCount = 0;
    int ignoredDraftArticlesCount = 0;
    int processedDraftArticlesCount = 0;
    long totalDraftArticlesCount = 0;
    try {
      List<String> draftArticles = Arrays.asList(contentInconsistentDraftArticlesUpgrade.split(";"));
      totalDraftArticlesCount = draftArticles.size();
      LOG.info("Total number of draft articles to be migrated: {}", totalDraftArticlesCount);
      for (List<String> draftArticlesChunk : ListUtils.partition(draftArticles, 10)) {
        Map<String, Integer> draftArticlesCountByTransaction = manageDraftArticles(draftArticlesChunk);
        int processedDraftArticlesCountByTransaction = draftArticlesChunk.size();
        processedDraftArticlesCount += processedDraftArticlesCountByTransaction;
        migratedDraftArticlesCount += processedDraftArticlesCountByTransaction
            - draftArticlesCountByTransaction.get("notMigratedDraftArticlesCountByTransaction")
            - draftArticlesCountByTransaction.get("ignoredDraftArticlesCountByTransaction");
        notMigratedDraftArticlesCount += draftArticlesCountByTransaction.get("notMigratedDraftArticlesCountByTransaction");
        ignoredDraftArticlesCount += draftArticlesCountByTransaction.get("ignoredDraftArticlesCountByTransaction");
        LOG.info("Draft articles migration progress: processed={}/{} succeeded={} ignored={} error={}",
                 processedDraftArticlesCount,
                 totalDraftArticlesCount,
                 migratedDraftArticlesCount,
                 ignoredDraftArticlesCount,
                 notMigratedDraftArticlesCount);
      }
    } catch (Exception e) {
      LOG.error("An error occurred when upgrading draft articles:", e);
    }
    if (totalDraftArticlesCount == migratedDraftArticlesCount) {
      LOG.info("End draft articles migration successful migration: total={} succeeded={} ignored={} error={}. It tooks {} ms.",
               totalDraftArticlesCount,
               migratedDraftArticlesCount,
               ignoredDraftArticlesCount,
               notMigratedDraftArticlesCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End draft articles migration with some errors: total={} succeeded={} ignored={} error={}. It tooks {} ms."
          + " The not migrated draft articles will be processed again next startup.",
               totalDraftArticlesCount,
               migratedDraftArticlesCount,
               ignoredDraftArticlesCount,
               notMigratedDraftArticlesCount,
               (System.currentTimeMillis() - startupTime));
      this.upgradeFailed = true;
      throw new IllegalStateException("Some draft articles wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  @Override
  public void afterUpgrade() {
    if (!upgradeFailed) {
      settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
                         Scope.APPLICATION.id(PLUGIN_NAME),
                         PLUGIN_EXECUTED_KEY,
                         SettingValue.create(true));
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext upgradePluginExecutionContext) {
    SettingValue<?> settingValue = settingService.get(Context.GLOBAL.id(PLUGIN_NAME),
                                                      Scope.APPLICATION.id(PLUGIN_NAME),
                                                      PLUGIN_EXECUTED_KEY);
    boolean shouldUpgrade = super.shouldProceedToUpgrade(newVersion, previousGroupVersion, upgradePluginExecutionContext);
    if (!shouldUpgrade && settingValue == null) {
      settingService.set(Context.GLOBAL.id(PLUGIN_NAME),
                         Scope.APPLICATION.id(PLUGIN_NAME),
                         PLUGIN_EXECUTED_KEY,
                         SettingValue.create(true));
    }
    return shouldUpgrade;
  }

  @Override
  public boolean isEnabled() {
    String isEnabledProperty = PropertyManager.getProperty(UPGRADE_PLUGIN_ENABLE.replace("{$0}", getName()));
    return isEnabledProperty != null && isEnabledProperty.equals("true");
  }

  public Map<String, Integer> manageDraftArticles(List<String> draftArticles) {
    int notMigratedDraftArticlesCountByTransaction = 0;
    int ignoredDraftArticlesCountByTransaction = 0;
    Map<String, Integer> draftArticlesCountByTransaction = new HashMap<String, Integer>();
    for (String draftArticle : draftArticles) {
      String draftArticleId = draftArticle.split(",")[0];
      String articleActivityId = draftArticle.split(",")[1];
      try {
        ExoSocialActivity articleActivity = activityManager.getActivity(articleActivityId);
        String poster = identityManager.getIdentity(Long.parseLong(articleActivity.getPosterId())).getRemoteId();
        Page draftArticlePage = noteService.getDraftNoteById(draftArticleId, poster);
        if (draftArticlePage != null) {
          News news = convertdraftArticlePageToNewEntity(draftArticlePage);
          News article = newsService.createNewsArticlePage(news, news.getAuthor());
          String oldNewsId = setArticleActivities(article.getId(), news.getSpaceId(), articleActivity);
          setArticleMetadatasItems(article.getId(), oldNewsId);
          setArticleCreateAndUpdateDate(article.getId(), news.getSpaceId(), draftArticlePage);
          LOG.info("Success migrating draft article with id '{}' and activity '{}'", draftArticleId, articleActivityId);
        } else {
          ignoredDraftArticlesCountByTransaction++;
          LOG.info("Ignore migrating not found draft article with id '{}' and activity '{}'. Continue to migrate other items",
                   draftArticleId,
                   articleActivityId);
        }

      } catch (Exception e) {
        notMigratedDraftArticlesCountByTransaction++;
        LOG.warn("Error migrating draft article with id '{}'. Continue to migrate other items", draftArticleId, e);
      }
    }
    draftArticlesCountByTransaction.put("notMigratedDraftArticlesCountByTransaction", notMigratedDraftArticlesCountByTransaction);
    draftArticlesCountByTransaction.put("ignoredDraftArticlesCountByTransaction", ignoredDraftArticlesCountByTransaction);
    return draftArticlesCountByTransaction;
  }

  private News convertdraftArticlePageToNewEntity(Page draftArticlePage) throws Exception {
    News news = new News();
    news.setId(draftArticlePage.getId());
    news.setTitle(draftArticlePage.getTitle());
    news.setName(news.getTitle());
    news.setProperties(draftArticlePage.getProperties());
    news.setBody(draftArticlePage.getContent());
    news.setAuthor(draftArticlePage.getAuthor());
    news.setDraftUpdaterUserName(draftArticlePage.getLastUpdater());
    Space space = spaceService.getSpaceByGroupId(draftArticlePage.getWikiOwner());
    news.setSpaceId(space.getId());
    news.setPublicationState("posted");
    news.setPublished(false);
    news.setActivityPosted(true);
    news.setCreationDate(draftArticlePage.getCreatedDate());
    news.setUpdateDate(draftArticlePage.getUpdatedDate());
    return news;
  }

  private String setArticleActivities(String articleId, String articleSpaceId, ExoSocialActivity articleActivity) {
    NewsPageObject articleMetaDataObject = new NewsPageObject(NewsServiceImpl.NEWS_METADATA_PAGE_OBJECT_TYPE,
                                                              articleId,
                                                              null,
                                                              Long.parseLong(articleSpaceId));
    MetadataItem articleMetadataItem = metadataService
                                                      .getMetadataItemsByMetadataAndObject(NewsServiceImpl.NEWS_METADATA_KEY,
                                                                                           articleMetaDataObject)
                                                      .stream()
                                                      .findFirst()
                                                      .orElse(null);
    String articleActivities = articleSpaceId + ":" + articleActivity.getId();
    if (articleMetadataItem != null) {
      Map<String, String> articleMetadataItemProperties = articleMetadataItem.getProperties();
      if (articleMetadataItemProperties == null) {
        articleMetadataItemProperties = new HashMap<>();
      }
      articleMetadataItemProperties.put("activities", articleActivities);
      articleMetadataItem.setProperties(articleMetadataItemProperties);
      metadataService.updateMetadataItem(articleMetadataItem, articleMetadataItem.getCreatorId(), false);
    }
    Map<String, String> templateParams = articleActivity.getTemplateParams() == null ? new HashMap<>()
                                                                                     : articleActivity.getTemplateParams();
    String oldNewsId = templateParams.get("newsId");
    templateParams.put("newsId", articleId);
    articleActivity.setTemplateParams(templateParams);
    articleActivity.setMetadataObjectId(articleId);
    articleActivity.setMetadataObjectType(NewsUtils.NEWS_METADATA_OBJECT_TYPE);
    activityManager.updateActivity(articleActivity, false);
    return oldNewsId;
  }

  @ExoTransactional
  private void setArticleMetadatasItems(String targetId, String sourceId) throws RepositoryException {
    EntityManager entityManager = entityManagerService.getEntityManager();
    String sqlStatement = "UPDATE SOC_METADATA_ITEMS SET OBJECT_ID = '" + targetId + "' WHERE OBJECT_ID = '" + sourceId + "';";
    Query query = entityManager.createNativeQuery(sqlStatement);
    query.executeUpdate();
  }

  private void setArticleCreateAndUpdateDate(String articleId, String articleSpaceId, Page draftArticlePage) throws Exception {
    Page articlePage = noteService.getNoteById(articleId);
    if (articlePage != null) {
      Date createdDate = draftArticlePage.getCreatedDate();
      Date updatedDate = draftArticlePage.getUpdatedDate();
      MetadataItem articleMetaDataItem =
                                       metadataService.getMetadataItemsByMetadataAndObject(NewsServiceImpl.NEWS_METADATA_KEY,
                                                                                           new NewsPageObject(NewsServiceImpl.NEWS_METADATA_PAGE_OBJECT_TYPE,
                                                                                                              articleId,
                                                                                                              null,
                                                                                                              Long.parseLong(articleSpaceId)))
                                                      .stream()
                                                      .findFirst()
                                                      .orElse(null);
      if (updatedDate != null) {
        articlePage.setUpdatedDate(updatedDate);
        articleMetaDataItem.setUpdatedDate(updatedDate.getTime());
      }
      if (createdDate != null) {
        articlePage.setCreatedDate(createdDate);
        articleMetaDataItem.setCreatedDate(createdDate.getTime());
      }
      noteService.updateNote(articlePage);
      metadataService.updateMetadataItem(articleMetaDataItem, articleMetaDataItem.getCreatorId(), false);
    }
  }
}
