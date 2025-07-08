package org.exoplatform.jcr.upgrade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;

@RunWith(MockitoJUnitRunner.class)
public class HideFoldersUpgradePluginTest {
  @Mock
  private RepositoryService repositoryService;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  private ManageableRepository repository;

  @Mock
  private RepositoryEntry repositoryEntry;

  @Mock
  private SessionProvider sessionProvider;
  @Mock
  private SettingService settingService;

  @Mock
  private Session session;

  @Mock
  private Workspace wokspace;



  @Test
  public void testHideFoldersUpgrade() throws Exception {
    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    lenient().when(repository.getConfiguration()).thenReturn(repositoryEntry);
    lenient().when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    Node node = mock(Node.class);
    Query query = mock(Query.class);
    QueryManager queryManager = mock(QueryManager.class);
    QueryResult queryResult = mock(QueryResult.class);
    when(session.getItem(anyString())).thenReturn(node);
    lenient().when(sessionProvider.getSession(any(), any(ManageableRepository.class))).thenReturn(session);
    InitParams initParams = new InitParams();
    verify(session, times(0)).save();
    lenient().when(node.getSession()).thenReturn(session);
    lenient().when(session.getWorkspace()).thenReturn(wokspace);
    lenient().when(wokspace.getQueryManager()).thenReturn(queryManager);
    lenient().when(node.getSession().getWorkspace().getQueryManager().createQuery(anyString(), anyString())).thenReturn(query);
    lenient().when(query.execute()).thenReturn(queryResult);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    lenient().when(queryResult.getNodes()).thenReturn(nodeIterator);
    lenient().when(nodeIterator.getSize()).thenReturn(3L);
    lenient().when(nodeIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
    lenient().when(nodeIterator.nextNode()).thenReturn(node);
    HideDefaultFoldersUpgradePlugin plugin = new HideDefaultFoldersUpgradePlugin(initParams,
            repositoryService,
            settingService,
            sessionProviderService);

    plugin.processUpgrade(null, null);
    verify(node, times(3)).save();
    verify(sessionProvider, times(1)).close();

  }
}
