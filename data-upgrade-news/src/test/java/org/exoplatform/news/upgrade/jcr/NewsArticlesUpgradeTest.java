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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.utils.MentionUtils;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataObject;
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
                                                  noteService);
  }
  @Test
  public void testProcessUpgrade() throws Exception {
    // Mock the session provider and session
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    Session session = mock(Session.class);
    when(sessionProvider.getSession(anyString(), any())).thenReturn(session);

    // Mock the query manager and query
    QueryManager queryManager = mock(QueryManager.class);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    Query query = mock(Query.class);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);

    // Mock the query result and nodes
    QueryResult queryResult = mock(QueryResult.class);
    when(query.execute()).thenReturn(queryResult);
    NodeIterator iterator = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(iterator);

    // Mock nodes
    Node node = mock(Node.class);
    when(iterator.hasNext()).thenReturn(true, false); // one node
    when(iterator.next()).thenReturn(node);

    // Mock the necessary properties of the node
    when(node.hasProperty("publication:currentState")).thenReturn(true);
    Property currentStateProperty = mock(Property.class);
    lenient().when(node.getProperty("publication:currentState")).thenReturn(currentStateProperty);
    lenient().when(currentStateProperty.getString()).thenReturn("published");

    Property pinnedProperty = mock(Property.class);
    lenient().when(node.getProperty("exo:pinned")).thenReturn(pinnedProperty);
    lenient().when(pinnedProperty.getString()).thenReturn("true");

    Property activityPostedProperty = mock(Property.class);
    lenient().when(node.getProperty("exo:newsActivityPosted")).thenReturn(activityPostedProperty);
    
    Property dateCreatedProperty = mock(Property.class);
    when(node.hasProperty("exo:dateCreated")).thenReturn(true);
    lenient().when(node.getProperty("exo:dateCreated")).thenReturn(dateCreatedProperty);
    lenient().when(dateCreatedProperty.getDate()).thenReturn(mock(Calendar.class));
    
    Property dateModifiedProperty = mock(Property.class);
    when(node.hasProperty("exo:dateModified")).thenReturn(true);
    lenient().when(node.getProperty("exo:dateModified")).thenReturn(dateModifiedProperty);
    lenient().when(dateModifiedProperty.getDate()).thenReturn(mock(Calendar.class));
    
    lenient().when(activityPostedProperty.getString()).thenReturn("true");

    lenient().when(node.getName()).thenReturn("newsName");

    COMMONS_UTILS.when(() -> CommonsUtils.getCurrentPortalOwner()).thenReturn("root");
    MENTION_UTILS.when(() -> MentionUtils.substituteUsernames(anyString(), anyString())).thenReturn("");
    News article = mock(News.class);
    when(article.getId()).thenReturn("1");
    when(article.getSpaceId()).thenReturn("1");
    when(newsService.createNewsArticlePage(any(News.class), anyString(), anyString())).thenReturn(article);

    PageVersion pageVersion = mock(PageVersion.class);
    when(noteService.getPublishedVersionByPageIdAndLang(anyLong(), nullable(String.class))).thenReturn(pageVersion);
    when(pageVersion.getId()).thenReturn("1");
    when(node.hasNode("illustration")).thenReturn(false);

    List<MetadataItem> metadataItems = new ArrayList<>();
    MetadataItem metadataItem = mock(MetadataItem.class);
    metadataItems.add(metadataItem);
    Property property = mock(Property.class);
    when(node.getProperty("exo:activities")).thenReturn(property);
    when(node.hasProperty("exo:activities")).thenReturn(true);
    when(property.getString()).thenReturn("1:1;");

    ExoSocialActivity exoSocialActivity = mock(ExoSocialActivity.class);
    when(activityManager.getActivity(any())).thenReturn(exoSocialActivity);
    when(metadataService.getMetadataItemsByMetadataAndObject(any(), any(MetadataObject.class))).thenReturn(metadataItems);
    lenient().when(node.hasNode("illustration")).thenReturn(true);
    Node illustrationNode = mock(Node.class);
    lenient().when(node.getNode("illustration")).thenReturn(illustrationNode);
    Node illustrationContentNode = mock(Node.class);
    lenient().when(illustrationNode.getNode("jcr:content")).thenReturn(illustrationContentNode);
    lenient().when(illustrationContentNode.getProperty("jcr:data")).thenReturn(mock(Property.class));
    lenient().when(illustrationContentNode.getProperty("jcr:mimeType")).thenReturn(mock(Property.class));
    lenient().when(illustrationNode.getProperty("exo:title")).thenReturn(mock(Property.class));
    
    lenient().when(node.hasNode("exo:attachmentsIds")).thenReturn(true);

    // Run the processUpgrade method
    newsArticlesUpgrade.processUpgrade("1.0", "2.0");

    // Verify that createNewsArticlePage was called
    verify(newsService, times(1)).createNewsArticlePage(any(News.class), anyString(), anyString());
    verify(noteService, times(1)).getPublishedVersionByPageIdAndLang(anyLong(), nullable(String.class));
    verify(metadataService, times(2)).getMetadataItemsByMetadataAndObject(any(), any(MetadataObject.class));
    verify(metadataService, times(2)).updateMetadataItem(any(), anyLong());
    verify(activityManager, times(1)).getActivity(any());
    verify(activityManager, times(1)).updateActivity(any(ExoSocialActivity.class), eq(true));
  }
}
