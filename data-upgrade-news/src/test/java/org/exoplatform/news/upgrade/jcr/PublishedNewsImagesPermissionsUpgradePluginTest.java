package org.exoplatform.news.upgrade.jcr;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import java.util.ArrayList;

import static org.exoplatform.news.upgrade.jcr.PublishedNewsImagesPermissionsUpgradePlugin.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PublishedNewsImagesPermissionsUpgradePluginTest {

    @Mock
    RepositoryService repositoryService;

    @Mock
    ManageableRepository repository;

    @Mock
    RepositoryEntry repositoryEntry;

    @Mock
    Session session;

    @Mock
    SessionProviderService sessionProviderService;

    @Mock
    SessionProvider sessionProvider;


    @Test
    public void publishedNewsImagesPermissionsUpgradePluginTest () throws Exception {
        InitParams initParams = new InitParams();
        ValueParam valueParam = new ValueParam();
        valueParam.setName("product.group.id");
        valueParam.setValue("org.exoplatform.platform");
        initParams.addParameter(valueParam);
        valueParam = new ValueParam();
        valueParam.setName(READ_PERMISSIONS);
        valueParam.setValue("read");
        initParams.addParameter(valueParam);
        valueParam = new ValueParam();
        valueParam.setName(PLATFORM_USERS_GROUP_IDENTITY);
        valueParam.setValue("*:/platform/users");
        initParams.addParameter(valueParam);

        when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
        when(repositoryService.getCurrentRepository()).thenReturn(repository);
        when(repository.getConfiguration()).thenReturn(repositoryEntry);
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
        when(nodeIterator.hasNext()).thenReturn(true, false);
        Node newsNode = mock(Node.class);
        Property property = mock(Property.class);
        when(nodeIterator.nextNode()).thenReturn(newsNode);
        when(newsNode.hasProperty("exo:body")).thenReturn(true);
        when(newsNode.getProperty("exo:body")).thenReturn(property);
        when(property.getString()).thenReturn("news body with image src=\"/portal/rest/images/repository/collaboration/123\"");
        ExtendedNode imageNode = mock(ExtendedNode.class);
        when(session.getNodeByUUID("123")).thenReturn(imageNode);
        when(imageNode.canAddMixin(EXO_PRIVILEGEABLE)).thenReturn(true);
        AccessControlList accessControlList = mock(AccessControlList.class);
        when(imageNode.getACL()).thenReturn(accessControlList);
        when(accessControlList.getPermissionEntries()).thenReturn(new ArrayList<>());
        //when
        PublishedNewsImagesPermissionsUpgradePlugin publishedNewsImagesPermissionsUpgradePlugin = new PublishedNewsImagesPermissionsUpgradePlugin(initParams, repositoryService, sessionProviderService);
        publishedNewsImagesPermissionsUpgradePlugin.processUpgrade(null, null);
        //then
        verify(imageNode, times(1)).setPermission("*:/platform/users", new String[]{"read"});
        verify(imageNode, times(1)).save();
    }
}
