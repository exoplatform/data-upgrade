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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.wiki.model.DraftPage;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.news.model.News;
import io.meeds.news.service.NewsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@RunWith(MockitoJUnitRunner.class)
public class ContentDraftArticlesUpgradeTest {

  @Mock
  private ActivityManager             activityManager;

  @Mock
  private IdentityManager             identityManager;

  @Mock
  private NoteService                 noteService;

  @Mock
  private NewsService                 newsService;

  @Mock
  private SpaceService                spaceService;

  @Mock
  private SettingService              settingService;

  @Mock
  private MetadataService             metadataService;

  @Mock
  private EntityManagerService        entityManagerService;

  private ContentDraftArticlesUpgrade contentDraftArticlesUpgrade;

  @Before
  public void setUp() {
    InitParams initParams = new InitParams();

    ValueParam valueParam1 = new ValueParam();
    valueParam1.setName("product.group.id");
    valueParam1.setValue("org.exoplatform.platform");
    ValueParam valueParam2 = new ValueParam();
    valueParam2.setName("content.inconsistent.draft.articles.upgrade");
    valueParam2.setValue("1,1;2,2");
    initParams.addParameter(valueParam1);
    initParams.addParameter(valueParam2);
    contentDraftArticlesUpgrade = new ContentDraftArticlesUpgrade(initParams,
                                                                  activityManager,
                                                                  identityManager,
                                                                  noteService,
                                                                  newsService,
                                                                  spaceService,
                                                                  settingService,
                                                                  metadataService,
                                                                  entityManagerService);
  }

  @Test
  public void testProcessUpgrade() throws Exception {

    ExoSocialActivity articleActivity = mock(ExoSocialActivity.class);
    when(articleActivity.getPosterId()).thenReturn("1");
    when(activityManager.getActivity(anyString())).thenReturn(articleActivity);
    Identity identity = mock(Identity.class);
    when(identity.getRemoteId()).thenReturn("root");
    when(identityManager.getIdentity(anyLong())).thenReturn(identity);
    DraftPage draftPage = mock(DraftPage.class);
    when(draftPage.getWikiOwner()).thenReturn("/spaces/test");
    when(noteService.getDraftNoteById(anyString(), anyString())).thenReturn(draftPage);
    Space space = mock(Space.class);
    when(spaceService.getSpaceByGroupId(anyString())).thenReturn(space);
    when(space.getId()).thenReturn("1");
    News article = mock(News.class);
    when(newsService.createNewsArticlePage(any(News.class), nullable(String.class))).thenReturn(article);
    List<MetadataItem> articleMetadataItems = Arrays.asList(mock(MetadataItem.class));
    when(metadataService.getMetadataItemsByMetadataAndObject(any(MetadataKey.class),
                                                             any(MetadataObject.class))).thenReturn(articleMetadataItems);

    EntityManager entityManager = mock(EntityManager.class);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    Page articlePage = mock(Page.class);
    when(noteService.getNoteById(nullable(String.class))).thenReturn(articlePage);

    // Run the processUpgrade method
    contentDraftArticlesUpgrade.processUpgrade("1.0", "2.0");

    verify(newsService, times(2)).createNewsArticlePage(any(News.class), nullable(String.class));
    verify(metadataService, times(4)).updateMetadataItem(any(), anyLong(), anyBoolean());
    verify(activityManager, times(2)).updateActivity(any(ExoSocialActivity.class), eq(false));
    verify(query, times(2)).executeUpdate();
    verify(noteService, times(2)).updateNote(any(Page.class));
  }

  @Test
  public void shouldProceedToUpgrade() {
    SettingValue settingValue = mock(SettingValue.class);
    UpgradePluginExecutionContext context = new UpgradePluginExecutionContext("0.9", 1);
    when(settingService.get(any(), any(), anyString())).thenReturn(null);
    contentDraftArticlesUpgrade.shouldProceedToUpgrade("0.9", "1.0", context);
    verify(settingService, times(1)).set(any(), any(), anyString(), any());
    reset(settingService);
    when(settingService.get(any(), any(), anyString())).thenReturn(settingValue);
    context.setVersion("1.2");
    contentDraftArticlesUpgrade.shouldProceedToUpgrade("1.2", "1.0", context);
    verify(settingService, times(0)).set(any(), any(), anyString(), any());
  }
}
