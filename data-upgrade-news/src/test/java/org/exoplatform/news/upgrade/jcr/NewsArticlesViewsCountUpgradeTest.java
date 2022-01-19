package org.exoplatform.news.upgrade.jcr;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "jdk.internal.reflect.*", "javax.xml.*", "org.apache.xerces.*", "org.xml.*" })

public class NewsArticlesViewsCountUpgradeTest {

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
    private Session session;

    @Mock
    private NodeIterator nodeIterator;

    @Mock
    private QueryManager queryManager;


    @Test
    public void testNewsArticleViewsCountUpgrade() throws RepositoryException {
        InitParams initParams = new InitParams();

        ValueParam valueParam = new ValueParam();
        valueParam.setName("product.group.id");
        valueParam.setValue("org.exoplatform.platform");
        initParams.addParameter(valueParam);

        Node testNode = mock(Node.class);
        Property property = mock(Property.class);
        Value value = mock(Value.class);
        Workspace workspace = mock(Workspace.class);
        Query query = mock(Query.class);
        QueryResult queryResult = mock(QueryResult.class);

        when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
        when(repositoryService.getCurrentRepository()).thenReturn(repository);
        when(repository.getConfiguration()).thenReturn(repositoryEntry);
        when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
        when(sessionProvider.getSession(any(), any())).thenReturn(session);
        when(nodeIterator.hasNext()).thenReturn(true, true, false);
        when(queryManager.createQuery(anyString(),any())).thenReturn(query);
        when(query.execute()).thenReturn(queryResult);
        when(queryResult.getNodes()).thenReturn(nodeIterator);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getQueryManager()).thenReturn(queryManager);
        when(nodeIterator.nextNode()).thenReturn(testNode);
        when(testNode.hasProperty("exo:viewsCount")).thenReturn(true);
        when(testNode.hasProperty("exo:viewers")).thenReturn(true);
        when(testNode.getProperty(anyString())).thenReturn(property);
        when(testNode.getProperty("exo:viewers").getValue()).thenReturn(value);
        when(testNode.getProperty("exo:viewers").getValue().getString()).thenReturn("user1,user2,user3");

        NewsArticlesViewsCountUpgrade plugin = new NewsArticlesViewsCountUpgrade(initParams,
                                                                                 repositoryService,
                                                                                 sessionProviderService);
        plugin.processUpgrade(null,null);
        assertEquals(2, plugin.getUpdatedNodes());
    }
}
