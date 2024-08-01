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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import io.meeds.notes.model.NotePageProperties;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.nodetype.NodeTypeManagerImpl;
import org.exoplatform.services.wcm.extensions.publication.lifecycle.authoring.AuthoringPublicationConstant;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.utils.MentionUtils;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;

import io.meeds.news.model.News;
import io.meeds.news.service.NewsService;

@RunWith(MockitoJUnitRunner.class)
public class NewsArticlesUpgradeTest {

  private static final MockedStatic<CommonsUtils> COMMONS_UTILS = mockStatic(CommonsUtils.class);

  private static final MockedStatic<MentionUtils> MENTION_UTILS = mockStatic(MentionUtils.class);

  @Mock
  private RepositoryService                       repositoryService;

  @Mock
  private SessionProviderService                  sessionProviderService;

  @Mock
  private ManageableRepository                    repository;

  @Mock
  private RepositoryEntry                         repositoryEntry;

  @Mock
  private ActivityManager                         activityManager;

  @Mock
  private SpaceService                            spaceService;

  @Mock
  private NewsService                             newsService;

  @Mock
  private NoteService                             noteService;

  @Mock
  private MetadataService                         metadataService;

  @Mock
  private FileService                             fileService;

  @Mock
  private IdentityManager                         identityManager;

  @Mock
  private IndexingService                         indexingService;

  @Mock
  private SettingService                          settingService;

  private NewsArticlesUpgrade                     newsArticlesUpgrade;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    COMMONS_UTILS.close();
    MENTION_UTILS.close();
  }

  @Before
  public void setUp() {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    newsArticlesUpgrade = new NewsArticlesUpgrade(initParams,
                                                  repositoryService,
                                                  sessionProviderService,
                                                  newsService,
                                                  spaceService,
                                                  activityManager,
                                                  metadataService,
                                                  fileService,
                                                  noteService,
                                                  identityManager,
                                                  indexingService,
                                                  settingService);
  }

  @Test
  public void testProcessUpgrade() throws Exception {
    // Mock the session provider and session
    Identity identity = mock(Identity.class);
    when(identity.getId()).thenReturn("1");
    when(identityManager.getOrCreateUserIdentity(anyString())).thenReturn(identity);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    Session session = mock(Session.class);
    when(sessionProvider.getSession(anyString(), any())).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    NodeTypeManagerImpl nodetypeManager = mock(NodeTypeManagerImpl.class);
    when(workspace.getNodeTypeManager()).thenReturn(nodetypeManager);
    when(nodetypeManager.hasNodeType(anyString())).thenReturn(true);

    // Mock the query manager and query
    QueryManager queryManager = mock(QueryManager.class);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    Query query = mock(Query.class);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);

    // Mock the query result and nodes
    QueryResult queryResult = mock(QueryResult.class);
    when(query.execute()).thenReturn(queryResult);
    NodeIterator iterator = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(iterator);

    // Mock nodes
    Node node1 = mock(Node.class);
    Node node2 = mock(Node.class);

    when(iterator.hasNext()).thenReturn(true, true, false);
    when(iterator.next()).thenReturn(node1, node2);

    // Mock the necessary properties of the node1
    when(node1.hasProperty("publication:currentState")).thenReturn(true);
    Property archivedProperty = mock(Property.class);
    when(node1.hasProperty("exo:archived")).thenReturn(true);
    when(node1.getProperty("exo:archived")).thenReturn(archivedProperty);
    when(archivedProperty.getBoolean()).thenReturn(false);
    Property publishedStateProperty = mock(Property.class);
    when(node1.getProperty("publication:currentState")).thenReturn(publishedStateProperty);
    when(publishedStateProperty.getString()).thenReturn("published");

    Property dateCreatedProperty = mock(Property.class);
    when(node1.hasProperty("exo:dateCreated")).thenReturn(true);
    when(node1.getProperty("exo:dateCreated")).thenReturn(dateCreatedProperty);
    when(dateCreatedProperty.getDate()).thenReturn(mock(Calendar.class));

    Property dateModifiedProperty = mock(Property.class);
    when(node1.hasProperty("exo:dateModified")).thenReturn(true);
    when(node1.getProperty("exo:dateModified")).thenReturn(dateModifiedProperty);
    when(dateModifiedProperty.getDate()).thenReturn(mock(Calendar.class));

    COMMONS_UTILS.when(() -> CommonsUtils.getCurrentPortalOwner()).thenReturn("root");
    MENTION_UTILS.when(() -> MentionUtils.substituteUsernames(anyString(), anyString())).thenReturn("");
    News article = mock(News.class);
    when(article.getId()).thenReturn("1");
    when(article.getSpaceId()).thenReturn("1");
    Page page = new Page();
    page.setId("1");
    page.setAuthor("user");
    when(identityManager.getOrCreateUserIdentity("user")).thenReturn(identity);
    PageVersion pageVersion = mock(PageVersion.class);
    when(pageVersion.getParent()).thenReturn(page);
    when(noteService.getPublishedVersionByPageIdAndLang(anyLong(), nullable(String.class))).thenReturn(pageVersion);
    when(noteService.getNoteById(anyString())).thenReturn(mock(Page.class));
    when(pageVersion.getId()).thenReturn("1");
    when(node1.hasNode("illustration")).thenReturn(false);

    List<MetadataItem> metadataItems = new ArrayList<>();
    MetadataItem metadataItem = mock(MetadataItem.class);
    metadataItems.add(metadataItem);
    when(node1.hasProperty("exo:activities")).thenReturn(true);
    Property activitiesProperty = mock(Property.class);
    when(node1.getProperty("exo:activities")).thenReturn(activitiesProperty);
    when(activitiesProperty.getString()).thenReturn("1:1;");

    when(node1.hasProperty("exo:viewsCount")).thenReturn(true);
    Property viewsCountProperty = mock(Property.class);
    when(node1.getProperty("exo:viewsCount")).thenReturn(viewsCountProperty);
    when(viewsCountProperty.getLong()).thenReturn(1L);

    when(node1.hasProperty("exo:viewers")).thenReturn(true);
    Property viewersProperty = mock(Property.class);
    when(node1.getProperty("exo:viewers")).thenReturn(viewersProperty);
    when(viewersProperty.getString()).thenReturn("1");

    ExoSocialActivity exoSocialActivity = mock(ExoSocialActivity.class);
    when(activityManager.getActivity(any())).thenReturn(exoSocialActivity);
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any(MetadataObject.class))).thenReturn(metadataItems);
    lenient().when(node1.hasNode("illustration")).thenReturn(true);
    Node illustrationNode = mock(Node.class);
    lenient().when(node1.getNode("illustration")).thenReturn(illustrationNode);
    Node illustrationContentNode = mock(Node.class);
    when(illustrationNode.getNode("jcr:content")).thenReturn(illustrationContentNode);
    when(illustrationContentNode.getProperty("jcr:data")).thenReturn(mock(Property.class));
    when(illustrationContentNode.getProperty("jcr:mimeType")).thenReturn(mock(Property.class));
    when(illustrationNode.getProperty("exo:title")).thenReturn(mock(Property.class));

    when(node1.hasProperty("exo:attachmentsIds")).thenReturn(true);
    Property attachmentsIdsProperty = mock(Property.class);
    when(node1.getProperty("exo:attachmentsIds")).thenReturn(attachmentsIdsProperty);
    Value value1 = mock(Value.class);
    when(value1.getString()).thenReturn("22121212");
    Value value2 = mock(Value.class);
    when(value2.getString()).thenReturn("443434343");
    Value[] attachmentsIdsPropertyValues = { value1, value2 };
    when(attachmentsIdsProperty.getValues()).thenReturn(attachmentsIdsPropertyValues);

    // Mock the necessary properties of the node2
    when(node2.hasProperty("publication:currentState")).thenReturn(true);
    Property stagedStateProperty = mock(Property.class);
    when(node2.getProperty("publication:currentState")).thenReturn(stagedStateProperty);
    when(stagedStateProperty.getString()).thenReturn("staged");
    when(node2.hasProperty(AuthoringPublicationConstant.START_TIME_PROPERTY)).thenReturn(true);
    Property startTimeProperty = mock(Property.class);
    when(node2.getProperty(AuthoringPublicationConstant.START_TIME_PROPERTY)).thenReturn(startTimeProperty);
    Calendar startTimePropertyCalendar = mock(Calendar.class);
    when(startTimeProperty.getDate()).thenReturn(startTimePropertyCalendar);
    when(startTimePropertyCalendar.getTime()).thenReturn(mock(Date.class));

    Method method = newsArticlesUpgrade.getClass().getDeclaredMethod("convertNewsNodeToNewEntity", Node.class, Node.class);
    method.setAccessible(true);
    News news1 = (News) method.invoke(newsArticlesUpgrade, node1, null);
    News news2 = (News) method.invoke(newsArticlesUpgrade, node2, null);
    when(newsService.createNewsArticlePage(news1, "")).thenReturn(article);
    when(newsService.createNewsArticlePage(news2, "")).thenReturn(article);
    // Run the processUpgrade method
    newsArticlesUpgrade.processUpgrade("1.0", "2.0");

    verify(newsService, times(2)).createNewsArticlePage(any(News.class), anyString());
    verify(noteService, times(2)).getPublishedVersionByPageIdAndLang(anyLong(), nullable(String.class));
    verify(metadataService, times(7)).getMetadataItemsByMetadataAndObject(any(), any(MetadataObject.class));
    verify(metadataService, times(7)).updateMetadataItem(any(), anyLong());
    verify(activityManager, times(1)).getActivity(any());
    verify(activityManager, times(1)).updateActivity(any(ExoSocialActivity.class), eq(false));
  }

  @Test
  public void shouldProceedToUpgrade() {
    SettingValue settingValue = mock(SettingValue.class);
    UpgradePluginExecutionContext context = new UpgradePluginExecutionContext("0.9", 1);
    when(settingService.get(any(), any(), anyString())).thenReturn(null);
    newsArticlesUpgrade.shouldProceedToUpgrade("0.9", "1.0", context);
    verify(settingService, times(1)).set(any(), any(), anyString(), any());
    reset(settingService);
    when(settingService.get(any(), any(), anyString())).thenReturn(settingValue);
    context.setVersion("1.2");
    newsArticlesUpgrade.shouldProceedToUpgrade("1.2", "1.0", context);
    verify(settingService, times(0)).set(any(), any(), anyString(), any());
  }
}
