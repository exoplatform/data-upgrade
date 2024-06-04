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
package org.exoplatform.news.upgrade.jcr;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.apache.commons.collections4.ListUtils;

import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.HTMLSanitizer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.services.wcm.extensions.publication.lifecycle.authoring.AuthoringPublicationConstant;
import org.exoplatform.services.wcm.publication.lifecycle.stageversion.StageAndVersionPublicationConstant;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.utils.MentionUtils;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.social.metadata.model.MetadataType;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.news.model.News;
import io.meeds.news.model.NewsPageObject;
import io.meeds.news.service.NewsService;
import io.meeds.news.utils.NewsUtils;

public class NewsArticlesUpgrade extends UpgradeProductPlugin {

  private static final Log         LOG                       = ExoLogger.getLogger(NewsArticlesUpgrade.class.getName());

  private RepositoryService        repositoryService;

  private SessionProviderService   sessionProviderService;

  private NewsService              newsService;

  private SpaceService             spaceService;

  private ActivityManager          activityManager;

  private MetadataService          metadataService;

  private FileService              fileService;

  private NoteService              noteService;

  private int                      migratedNewsArticlesCount = 0;

  public static final MetadataType NEWS_METADATA_TYPE        = new MetadataType(1000, "news");

  public static final String       NEWS_METADATA_NAME        = "news";

  private static final MetadataKey NEWS_METADATA_KEY         =
                                                     new MetadataKey(NEWS_METADATA_TYPE.getName(), NEWS_METADATA_NAME, 0);

  public NewsArticlesUpgrade(InitParams initParams,
                             RepositoryService repositoryService,
                             SessionProviderService sessionProviderService,
                             NewsService newsService,
                             SpaceService spaceService,
                             ActivityManager activityManager,
                             MetadataService metadataService,
                             FileService fileService,
                             NoteService noteService) {
    super(initParams);
    this.repositoryService = repositoryService;
    this.sessionProviderService = sessionProviderService;
    this.newsService = newsService;
    this.spaceService = spaceService;
    this.activityManager = activityManager;
    this.metadataService = metadataService;
    this.fileService = fileService;
    this.noteService = noteService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of news articles");
    SessionProvider sessionProvider = null;
    int notMigratedNewsArticlesCount = 0;
    int processedNewsArticlesCount = 0;
    long totalNewsArticlesCount = 0;
    try {
      sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      Session session = sessionProvider.getSession(
                                                   repositoryService.getCurrentRepository()
                                                                    .getConfiguration()
                                                                    .getDefaultWorkspaceName(),
                                                   repositoryService.getCurrentRepository());
      String queryString =
                         "SELECT * FROM exo:news WHERE jcr:path LIKE '/Groups/spaces/%/News/%' and exo:archived = 'false' order by exo:dateModified DESC";
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString, Query.SQL);

      Iterator<Node> newsIterator = query.execute().getNodes();
      List<Node> newsArticlesNodes = new ArrayList<Node>();
      while (newsIterator.hasNext()) {
        newsArticlesNodes.add(newsIterator.next());
      }
      totalNewsArticlesCount = newsArticlesNodes.size();
      LOG.info("Total number of news articles to be migrated: {}", totalNewsArticlesCount);
      for (List<Node> newsArticlesChunk : ListUtils.partition(newsArticlesNodes, 10)) {
        int notMigratedNewsArticlesCountByTransaction = manageNewsArticles(newsArticlesChunk, session);
        int processedNewsArticlesCountByTransaction = newsArticlesChunk.size();
        processedNewsArticlesCount += processedNewsArticlesCountByTransaction;
        migratedNewsArticlesCount += processedNewsArticlesCountByTransaction - notMigratedNewsArticlesCountByTransaction;
        notMigratedNewsArticlesCount += notMigratedNewsArticlesCountByTransaction;
        LOG.info("News articles migration progress: processed={}/{} succeeded={} error={}",
                 processedNewsArticlesCount,
                 totalNewsArticlesCount,
                 migratedNewsArticlesCount,
                 notMigratedNewsArticlesCount);
      }
    } catch (Exception e) {
      LOG.error("An error occurred when upgrading news articles:", e);
    } finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
    if (notMigratedNewsArticlesCount == 0) {
      LOG.info("End news articles migration successful migration: total={} succeeded={} error={}. It tooks {} ms.",
               totalNewsArticlesCount,
               migratedNewsArticlesCount,
               notMigratedNewsArticlesCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End news articles migration with some errors: total={} succeeded={} error={}. It tooks {} ms."
          + " The not migrated news articles will be processed again next startup.",
               totalNewsArticlesCount,
               migratedNewsArticlesCount,
               notMigratedNewsArticlesCount,
               (System.currentTimeMillis() - startupTime));
      throw new IllegalStateException("Some news articles wasn't executed successfully. It will be re-attempted next startup");
    }
  }

  public int manageNewsArticles(List<Node> newsArticlesNodes, Session session) throws Exception {
    int notMigratedNewsArticlesCount = 0;
    for (Node newsArticleNode : newsArticlesNodes) {
      News article = null;
      News draftArticle = null;
      try {
        News news = convertNewsNodeToNewEntity(newsArticleNode);
        LOG.info("Migrating news article with id '{}' and title '{}'", newsArticleNode.getUUID(), news.getTitle());
        Space space = spaceService.getSpaceById(news.getSpaceId());

        // existing published and staged articles
        if (getStringProperty(newsArticleNode, "publication:currentState").equals("staged")
            || getStringProperty(newsArticleNode, "publication:currentState").equals("published")) {

          article = newsService.createNewsArticlePage(news, news.getAuthor());
          PageVersion pageVersion = noteService.getPublishedVersionByPageIdAndLang(Long.parseLong(article.getId()), null);
          setArticleIllustration(pageVersion.getId(), article.getSpaceId(), newsArticleNode, "newsPageVersion");
          setArticleAttachments(pageVersion.getId(), article.getSpaceId(), newsArticleNode, "newsPageVersion");
          /* upgrade news id for news targets and favorite metadatata items */
          setArticleMetadatasItems(article.getId(), getStringProperty(newsArticleNode, "jcr:uuid"));
          if (getStringProperty(newsArticleNode, "publication:currentState").equals("published")) {
            setArticleActivities(article, newsArticleNode);
          }
          if (getStringProperty(newsArticleNode, "publication:currentState").equals("staged")) {
            setSchedulePostDate(article.getId(), article.getSpaceId(), newsArticleNode, "newsPage");
          }
          // set the update and the created date
          setArticleCreateAndUpdateDate(article.getId(), article.getSpaceId(), newsArticleNode);
        } else if (getStringProperty(newsArticleNode, "publication:currentState").equals("draft")) {

          // drafts of not existing articles
          if (!newsArticleNode.hasProperty(AuthoringPublicationConstant.LIVE_REVISION_PROP)) {
            // upgrade the drafts of not existing articles
            /* attachments will not be migrated for drafts */
            draftArticle = newsService.createDraftArticleForNewPage(news,
                                                                         space.getGroupId(),
                                                                         news.getAuthor(),
                                                                         news.getCreationDate().getTime());
            setArticleIllustration(draftArticle.getId(), draftArticle.getSpaceId(), newsArticleNode, "newsDraftPage");
          } else {// drafts of existing articles

            // upgrade existing articles
            String versionNodeUUID = newsArticleNode.getProperty(AuthoringPublicationConstant.LIVE_REVISION_PROP).getString();
            Node versionNode = newsArticleNode.getVersionHistory().getSession().getNodeByUUID(versionNodeUUID);
            Node publishedNode = versionNode.getNode("jcr:frozenNode");
            News publishedNews = convertNewsNodeToNewEntity(publishedNode);
            article = newsService.createNewsArticlePage(publishedNews, publishedNews.getAuthor());
            PageVersion pageVersion = noteService.getPublishedVersionByPageIdAndLang(Long.parseLong(article.getId()), null);
            setArticleIllustration(pageVersion.getId(), article.getSpaceId(), publishedNode, "newsPageVersion");
            setArticleAttachments(pageVersion.getId(), article.getSpaceId(), publishedNode, "newsPageVersion");
            /* upgrade news id for news targets and favorite metadatata items */
            setArticleMetadatasItems(article.getId(), getStringProperty(publishedNode, "jcr:uuid"));
            setArticleActivities(article, publishedNode);
            Page publishedPage = noteService.getNoteById(article.getId());

            // upgrade the drafts of existing articles
            /* attachments will not be migrated for drafts */
            News draftForExistingArticle = newsService.createDraftForExistingPage(news,
                                                                       news.getAuthor(),
                                                                       publishedPage,
                                                                       news.getCreationDate().getTime());
            setArticleIllustration(draftForExistingArticle.getId(), draftForExistingArticle.getSpaceId(), newsArticleNode, "newsLatestDraftPage");
            // set the update and the created date
            setArticleCreateAndUpdateDate(article.getId(), article.getSpaceId(), newsArticleNode);
          }
        }
        newsArticleNode.remove();
        session.save();
      } catch (Exception e) {
        LOG.warn("Error migrating news article with id '{}'. Continue to migrate other items", newsArticleNode.getUUID(), e);
        if (article != null) {
          newsService.deleteArticle(article, article.getAuthor());
          setArticleMetadatasItems(newsArticleNode.getUUID(), article.getId());
        }
        else if (draftArticle != null) {
          newsService.deleteDraftArticle(draftArticle.getId(), draftArticle.getAuthor(), true);
        }
        notMigratedNewsArticlesCount++;
      }
    }
    return notMigratedNewsArticlesCount;
  }

  private News convertNewsNodeToNewEntity(Node newsNode) throws Exception {
    News news = new News();
    String portalOwner = CommonsUtils.getCurrentPortalOwner();
    news.setTitle(getStringProperty(newsNode, "exo:title"));
    news.setName(news.getTitle() + "_" + newsNode.getUUID());
    news.setSummary(getStringProperty(newsNode, "exo:summary"));
    String body = getStringProperty(newsNode, "exo:body");
    String sanitizedBody = HTMLSanitizer.sanitize(body);
    sanitizedBody = sanitizedBody.replaceAll("&#64;", "@");
    news.setBody(MentionUtils.substituteUsernames(portalOwner, sanitizedBody));
    news.setAuthor(getStringProperty(newsNode, "exo:author"));
    news.setSpaceId(getStringProperty(newsNode, "exo:spaceId"));
    news.setAudience(getStringProperty(newsNode, "exo:audience"));
    news.setPublicationState((getStringProperty(newsNode, StageAndVersionPublicationConstant.CURRENT_STATE).equals("published")
        || newsNode.getName().equals("jcr:frozenNode")) ? "posted"
                                                        : getStringProperty(newsNode,
                                                                            StageAndVersionPublicationConstant.CURRENT_STATE));
    news.setPublished(getBooleanProperty(newsNode, "exo:pinned"));
    news.setActivityPosted(getBooleanProperty(newsNode, "exo:newsActivityPosted"));
    news.setUploadId("");
    news.setCreationDate(getDateProperty(newsNode, "exo:dateCreated"));
    news.setUpdateDate(getDateProperty(newsNode, "exo:dateModified"));
    return news;
  }
  
  private boolean getBooleanProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getBoolean();
    }
    return false;
  }

  private String getStringProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getString();
    }
    return "";
  }

  private Date getDateProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getDate().getTime();
    }
    return null;
  }

  private Long saveArticleIllustration(InputStream articleIllustrationFileInputStream,
                                       String fileName,
                                       String mimeType,
                                       long uploadSize) {
    try {
      FileItem articleIllustrationFileItem = new FileItem(null,
                                                          fileName,
                                                          mimeType,
                                                          "news",
                                                          uploadSize,
                                                          new Date(),
                                                          IdentityConstants.SYSTEM,
                                                          false,
                                                          articleIllustrationFileInputStream);
      articleIllustrationFileItem = fileService.writeFile(articleIllustrationFileItem);
      return articleIllustrationFileItem != null
          && articleIllustrationFileItem.getFileInfo() != null ? articleIllustrationFileItem.getFileInfo().getId() : null;
    } catch (Exception e) {
      throw new IllegalStateException("Error while saving article illustration file", e);
    }
  }

  private void setArticleIllustration(String articleId,
                                      String spaceId,
                                      Node newsNode,
                                      String articleObjectType) throws RepositoryException {
    if (newsNode.hasNode("illustration")) {
      Node illustrationNode = newsNode.getNode("illustration");
      Node illustrationContentNode = illustrationNode.getNode("jcr:content");
      InputStream illustrationNodeInputStream = illustrationContentNode.getProperty("jcr:data").getStream();
      String mimetype = illustrationContentNode.getProperty("jcr:mimeType").getString();
      String illustrationNodeName = illustrationNode.getProperty("exo:title").getString();
      Long articleIllustrationId = saveArticleIllustration(illustrationNodeInputStream, illustrationNodeName, mimetype, 0);
      MetadataObject articleMetaDataObject = new MetadataObject(articleObjectType, articleId, null, Long.parseLong(spaceId));

      MetadataItem articleMetadataItem = metadataService.getMetadataItemsByMetadataAndObject(NEWS_METADATA_KEY,
                                                                                             articleMetaDataObject)
                                                        .get(0);
      if (articleMetadataItem != null) {
        Map<String, String> articleMetadataItemProperties = articleMetadataItem.getProperties();
        if (articleMetadataItemProperties == null) {
          articleMetadataItemProperties = new HashMap<>();
        }
        articleMetadataItemProperties.put("illustrationId", String.valueOf(articleIllustrationId));
        articleMetadataItem.setProperties(articleMetadataItemProperties);
        metadataService.updateMetadataItem(articleMetadataItem, articleMetadataItem.getCreatorId());
      }
    }
  }

  private void setArticleActivities(News article, Node newsNode) throws RepositoryException {
    NewsPageObject articleMetaDataObject = new NewsPageObject("newsPage",
                                                              article.getId(),
                                                              null,
                                                              Long.parseLong(article.getSpaceId()));
    MetadataItem articleMetadataItem =
                                     metadataService.getMetadataItemsByMetadataAndObject(NEWS_METADATA_KEY, articleMetaDataObject)
                                                    .stream().findFirst().orElse(null);
    String articleActivities = getStringProperty(newsNode, "exo:activities");
    if (articleMetadataItem != null) {
      Map<String, String> articleMetadataItemProperties = articleMetadataItem.getProperties();
      if (articleMetadataItemProperties == null) {
        articleMetadataItemProperties = new HashMap<>();
      }
      articleMetadataItemProperties.put("activities", articleActivities);
      articleMetadataItem.setProperties(articleMetadataItemProperties);
      metadataService.updateMetadataItem(articleMetadataItem, articleMetadataItem.getCreatorId());
    }
    String newsActivityId = articleActivities.split(";")[0].split(":")[1];
    ExoSocialActivity activity = activityManager.getActivity(newsActivityId);
    if (activity != null) {
      Map<String, String> templateParams = activity.getTemplateParams() == null ? new HashMap<>() : activity.getTemplateParams();
      templateParams.put("newsId", article.getId());
      activity.setTemplateParams(templateParams);
      activity.setMetadataObjectId(article.getId());
      activity.setMetadataObjectType(NewsUtils.NEWS_METADATA_OBJECT_TYPE);
      activityManager.updateActivity(activity, true);
    }
  }

  private void setArticleAttachments(String articleId,
                                     String spaceId,
                                     Node newsNode,
                                     String articleObjectType) throws RepositoryException {
    if (newsNode.hasProperty("exo:attachmentsIds")) {
      Property attachmentsIdsProperty = newsNode.getProperty("exo:attachmentsIds");
      String attachmentsIds = "";
      for (Value value : attachmentsIdsProperty.getValues()) {
        String attachmentId = value.getString();
        attachmentsIds += attachmentId + ";";
      }
      MetadataObject articleMetaDataObject = new MetadataObject(articleObjectType, articleId, null, Long.parseLong(spaceId));
      MetadataItem articleMetadataItem = metadataService.getMetadataItemsByMetadataAndObject(NEWS_METADATA_KEY,
                                                                                             articleMetaDataObject)
                                                        .get(0);
      if (articleMetadataItem != null) {
        Map<String, String> articleMetadataItemProperties = articleMetadataItem.getProperties();
        if (articleMetadataItemProperties == null) {
          articleMetadataItemProperties = new HashMap<>();
        }
        articleMetadataItemProperties.put("attachmentsIds", attachmentsIds);
        articleMetadataItem.setProperties(articleMetadataItemProperties);
        metadataService.updateMetadataItem(articleMetadataItem, articleMetadataItem.getCreatorId());
      }
    }
  }

  private void setSchedulePostDate(String articleId,
                                   String spaceId,
                                   Node newsNode,
                                   String articleObjectType) throws RepositoryException {
    if (newsNode.hasProperty(AuthoringPublicationConstant.START_TIME_PROPERTY)) {

      Calendar startPostDate = newsNode.getProperty(AuthoringPublicationConstant.START_TIME_PROPERTY).getDate();

      // create a SimpleDateFormat to format the date and then save it as string
      SimpleDateFormat defaultFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      defaultFormat.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
      String scheduledPostDate = defaultFormat.format(startPostDate.getTime());
      MetadataObject articleMetaDataObject = new MetadataObject(articleObjectType, articleId, null, Long.parseLong(spaceId));
      MetadataItem articleMetadataItem = metadataService.getMetadataItemsByMetadataAndObject(NEWS_METADATA_KEY,
                                                                                             articleMetaDataObject)
                                                        .stream().findFirst().orElse(null);
      if (articleMetadataItem != null) {
        Map<String, String> articleMetadataItemProperties = articleMetadataItem.getProperties();
        if (articleMetadataItemProperties == null) {
          articleMetadataItemProperties = new HashMap<>();
        }
        articleMetadataItemProperties.put("schedulePostDate", scheduledPostDate);
        articleMetadataItem.setProperties(articleMetadataItemProperties);
        metadataService.updateMetadataItem(articleMetadataItem, articleMetadataItem.getCreatorId());
      }
    }
  }

  private void setArticleMetadatasItems(String articleId, String newsNodeId) throws RepositoryException {
    MetadataObject metadataObject = new MetadataObject(NewsUtils.NEWS_METADATA_OBJECT_TYPE, newsNodeId);
    List<MetadataItem> metadataItems = metadataService.getMetadataItemsByObject(metadataObject);
    for (MetadataItem metadataItem : metadataItems) {
      metadataItem.setObjectId(articleId);
      metadataService.updateMetadataItem(metadataItem, metadataItem.getCreatorId());
    }
  }

  private void setArticleCreateAndUpdateDate(String articleId, String spaceId, Node newsNode) throws Exception {
    Page articlePage = noteService.getNoteById(articleId);
    if (articlePage != null) {
      Date createDate = getDateProperty(newsNode, "exo:createDate");
      Date updateDate = getDateProperty(newsNode, "exo:dateModified");
      MetadataItem articleMetaData = metadataService.getMetadataItemsByMetadataAndObject(NEWS_METADATA_KEY, new NewsPageObject("newsPage", articleId, null, Long.parseLong(spaceId))).stream().findFirst().orElse(null);
      if (updateDate != null) {
        articlePage.setUpdatedDate(updateDate);
        articleMetaData.setUpdatedDate(updateDate.getTime());
      }
      if (createDate != null) {
        articlePage.setCreatedDate(createDate);
        articleMetaData.setCreatedDate(createDate.getTime());
      }
      noteService.updateNote(articlePage);
      metadataService.updateMetadataItem(articleMetaData, articleMetaData.getCreatorId());
    }
  }
}

