package org.exoplatform.news.upgrade.es;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;

@RunWith(PowerMockRunner.class)
public class NewsIndexingUpgradePluginTest {

  @Mock
  RepositoryService      repositoryService;

  @Mock
  IndexingService        indexingService;

  @Mock
  SessionProviderService sessionProviderService;

  @Mock
  ManageableRepository   repository;

  @Mock
  RepositoryEntry        repositoryEntry;

  @Mock
  SessionProvider        sessionProvider;

  @Mock
  Session                session;

  @Test
  public void testOdlNewsIndexing() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.addons.news");
    initParams.addParameter(valueParam);

    when(sessionProviderService.getSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    when(sessionProvider.getSession(any(), any())).thenReturn(session);
    QueryManager qm = mock(QueryManager.class);
    Workspace workSpace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workSpace);
    when(workSpace.getQueryManager()).thenReturn(qm);
    Query query = mock(QueryImpl.class);
    when(qm.createQuery(anyString(), anyString())).thenReturn(query);
    QueryResult queryResult = mock(QueryResult.class);
    when(query.execute()).thenReturn(queryResult);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(queryResult.getNodes()).thenReturn(nodeIterator);
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node newsNode = mock(Node.class);
    Property property = mock(Property.class);
    when(newsNode.getProperty("exo:activities")).thenReturn(property);
    when(property.getString()).thenReturn("1:1;1:2;1:3");
    when(nodeIterator.nextNode()).thenReturn(newsNode);

    NewsIndexingUpgradePlugin newsIndexingUpgradePlugin = new NewsIndexingUpgradePlugin(initParams,
                                                                                        repositoryService,
                                                                                        indexingService,
                                                                                        sessionProviderService);
    newsIndexingUpgradePlugin.processUpgrade(null, null);

    assertEquals(2, newsIndexingUpgradePlugin.getNewsIndexingCount());
  }
}
