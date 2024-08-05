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

import io.meeds.notes.model.NoteMetadataObject;
import org.apache.commons.collections4.MapUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataFilter;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;
import org.exoplatform.wiki.utils.Utils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContentArticlePropertiesUpgrade extends UpgradeProductPlugin {

  private static final Log         LOG                                    =
                                       ExoLogger.getLogger(ContentArticlePropertiesUpgrade.class);

  private final NoteService        noteService;

  private final MetadataService    metadataService;

  private final IdentityManager    identityManager;

  private final SpaceService       spaceService;

  private final UserACL            userACL;

  private final SettingService     settingService;

  private static final MetadataKey NOTES_METADATA_KEY                     =
                                                      new MetadataKey("notes", Utils.NOTES_METADATA_OBJECT_TYPE, 0);

  public static final String       NEWS_METADATA_NAME                     = "news";

  public static final String       NEWS_METADATA_DRAFT_OBJECT_TYPE        = "newsDraftPage";

  public static final String       NOTE_METADATA_PAGE_OBJECT_TYPE         = "notePage";

  public static final String       NOTE_METADATA_DRAFT_PAGE_OBJECT_TYPE   = "noteDraftPage";

  public static final String       NEWS_METADATA_PAGE_VERSION_OBJECT_TYPE = "newsPageVersion";

  public static final String       NEWS_METADATA_LATEST_DRAFT_OBJECT_TYPE = "newsLatestDraftPage";

  public static final String       CONTENT_ILLUSTRATION_ID                = "illustrationId";

  public static final String       SUMMARY                                = "summary";

  public static final String       FEATURED_IMAGE_ID                      = "featuredImageId";

  private static final String      FEATURED_IMAGE_UPDATED_DATE            = "featuredImageUpdatedDate";

  private static final String      ARTICLES_UPGRADE_PLUGIN_NAME           = "NewsArticlesUpgradePlugin";

  private static final String      ARTICLES_UPGRADE_EXECUTED_KEY          = "articlesUpgradeExecuted";

  public ContentArticlePropertiesUpgrade(InitParams initParams,
                                         NoteService noteService,
                                         MetadataService metadataService,
                                         IdentityManager identityManager,
                                         SpaceService spaceService,
                                         UserACL userACL,
                                         SettingService settingService) {
    super(initParams);
    this.noteService = noteService;
    this.metadataService = metadataService;
    this.identityManager = identityManager;
    this.spaceService = spaceService;
    this.userACL = userACL;
    this.settingService = settingService;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    long startupTime = System.currentTimeMillis();
    LOG.info("Start upgrade of content page properties");
    int notMigratedContentPagesPropertiesCount;
    int processedContentPagesPropertiesCount = 0;
    int totalContentPagesPropertiesCount = 0;
    int ignoredContentPagesPropertiesCount = 0;
    try {
      MetadataFilter metadataFilter = getMetadataFilter();
      metadataFilter.setMetadataObjectTypes(List.of(NEWS_METADATA_PAGE_VERSION_OBJECT_TYPE,
                                                    NEWS_METADATA_DRAFT_OBJECT_TYPE,
                                                    NEWS_METADATA_LATEST_DRAFT_OBJECT_TYPE));
      List<MetadataItem> metadataItems = metadataService.getMetadataItemsByFilter(metadataFilter, 0, 0);
      totalContentPagesPropertiesCount = metadataItems.size();
      for (MetadataItem metadataItem : metadataItems) {
        if (metadataItem != null && !MapUtils.isEmpty(metadataItem.getProperties())) {
          Map<String, String> contentProperties = metadataItem.getProperties();
          Page page = null;
          String objectType = NOTE_METADATA_PAGE_OBJECT_TYPE;
          if (metadataItem.getObjectType().equals(NEWS_METADATA_PAGE_VERSION_OBJECT_TYPE)) {
            PageVersion pageVersion = noteService.getPageVersionById(Long.valueOf(metadataItem.getObjectId()));
            if (pageVersion != null && pageVersion.getParent() != null) {
              page = pageVersion.getParent();
            }
          } else {
            page = noteService.getDraftNoteById(metadataItem.getObjectId(), userACL.getSuperUser());
            objectType = NOTE_METADATA_DRAFT_PAGE_OBJECT_TYPE;
          }
          if (page != null && page.getAuthor() != null) {
            NoteMetadataObject noteMetadataObject = buildNoteMetadataObject(page, null, objectType);
            MetadataItem noteMetadataItem = getNoteMetadataItem(page, null, objectType);

            if (noteMetadataItem != null) {
              LOG.info("ContentArticlePropertiesUpgrade: Ignore : Content page properties already migrated");
              ignoredContentPagesPropertiesCount++;
            } else {
              Map<String, String> noteProperties = new HashMap<>();
              long creatorId = Long.parseLong(identityManager.getOrCreateUserIdentity(page.getAuthor()).getId());

              if (contentProperties.getOrDefault(CONTENT_ILLUSTRATION_ID, null) != null) {
                noteProperties.put(FEATURED_IMAGE_ID, contentProperties.get(CONTENT_ILLUSTRATION_ID));
                noteProperties.put(FEATURED_IMAGE_UPDATED_DATE, String.valueOf(new Date().getTime()));
              }
              noteProperties.put(SUMMARY, contentProperties.get(SUMMARY));
              metadataService.createMetadataItem(noteMetadataObject, NOTES_METADATA_KEY, noteProperties, creatorId);
              processedContentPagesPropertiesCount++;
              LOG.info("ContentArticlePropertiesUpgrade: Processed content page properties: {}/{}",
                       processedContentPagesPropertiesCount,
                       totalContentPagesPropertiesCount);
            }
          } else {
            ignoredContentPagesPropertiesCount++;
            LOG.warn("ContentArticlePropertiesUpgrade: Content page properties ignored due to data inconsistency: "
                + "page exists: {}, page name: {}",
                     "ObjectType: {}",
                     "Page Id: {}",
                     "Page author: {}",
                     page != null,
                     page != null ? page.getName() : null,
                     objectType,
                     page != null ? page.getId() : null,
                     null);
          }
        } else {
          LOG.info("ContentArticlePropertiesUpgrade: Ignore : Content page properties are empty");
          ignoredContentPagesPropertiesCount++;
        }
      }
    } catch (Exception e) {
      LOG.error("An error occurred while Migrating content pages properties:", e);
    }
    notMigratedContentPagesPropertiesCount = totalContentPagesPropertiesCount
        - (processedContentPagesPropertiesCount + ignoredContentPagesPropertiesCount);
    if (notMigratedContentPagesPropertiesCount == 0) {
      LOG.info("End ContentArticlePropertiesUpgrade successful migration: total={} processed={} ignored={} error={}. It took {} ms.",
               totalContentPagesPropertiesCount,
               processedContentPagesPropertiesCount,
               ignoredContentPagesPropertiesCount,
               notMigratedContentPagesPropertiesCount,
               (System.currentTimeMillis() - startupTime));
    } else {
      LOG.warn("End ContentArticlePropertiesUpgrade with some errors: total={} processed={} ignored={} error={}. It took {} ms."
          + " The not migrated news articles will be processed again next startup.",
               totalContentPagesPropertiesCount,
               processedContentPagesPropertiesCount,
               ignoredContentPagesPropertiesCount,
               notMigratedContentPagesPropertiesCount,
               (System.currentTimeMillis() - startupTime));
      throw new IllegalStateException("Some content page properties weren't migrated successfully. It will be re-attempted next startup");
    }
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

  private MetadataFilter getMetadataFilter() {
    MetadataFilter metadataFilter = new MetadataFilter();
    metadataFilter.setMetadataName(NEWS_METADATA_NAME);
    metadataFilter.setMetadataTypeName(NEWS_METADATA_NAME);
    return metadataFilter;
  }

  private NoteMetadataObject buildNoteMetadataObject(Page note, String lang, String objectType) {
    Space space = spaceService.getSpaceByGroupId(note.getWikiOwner());
    long spaceId = space != null ? Long.parseLong(space.getId()) : 0L;
    String noteId = String.valueOf(note.getId());
    noteId = lang != null ? noteId + "-" + lang : noteId;
    return new NoteMetadataObject(objectType, noteId, note.getParentPageId(), spaceId);
  }

  private MetadataItem getNoteMetadataItem(Page note, String lang, String objectType) {
    NoteMetadataObject noteMetadataObject = buildNoteMetadataObject(note, lang, objectType);
    return metadataService.getMetadataItemsByMetadataAndObject(NOTES_METADATA_KEY, noteMetadataObject)
                          .stream()
                          .findFirst()
                          .orElse(null);
  }

}
