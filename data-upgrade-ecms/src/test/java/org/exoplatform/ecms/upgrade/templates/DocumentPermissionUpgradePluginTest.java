package org.exoplatform.ecms.upgrade.templates;

import static org.mockito.Mockito.*;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.cms.BasePath;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.print.Doc;
import java.security.Permission;

@RunWith(PowerMockRunner.class)
public class DocumentPermissionUpgradePluginTest {
    @Mock
    NodeHierarchyCreator nodeHierarchyCreator;

    @Mock
    RepositoryService repositoryService;

    @Mock
    SessionProviderService sessionProviderService;

    @Mock
    ManageableRepository repository;

    @Mock
    RepositoryEntry repositoryEntry;

    @Mock
    SessionProvider sessionProvider;

    @Mock
    Session session;

    @Test
    public void testDocumentsJcrNodeMigration() throws Exception {
        InitParams initParams = new InitParams();

        ValueParam valueParam = new ValueParam();
        valueParam.setName("product.group.id");
        valueParam.setValue("org.exoplatform.ecms");
        initParams.addParameter(valueParam);

        when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
        when(repositoryService.getCurrentRepository()).thenReturn(repository);
        when(repository.getConfiguration()).thenReturn(repositoryEntry);
        when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
        when(sessionProvider.getSession(any(), any())).thenReturn(session);
        when(nodeHierarchyCreator.getJcrPath(anyString())).thenReturn(BasePath.CMS_GROUPS_PATH);
        Node spacesRootNode = mock(Node.class);
        when((Node)session.getItem(anyString())).thenReturn(spacesRootNode);
        Node spaceNode = mock(Node.class);
        when((Node) session.getItem(anyString())).thenReturn(spacesRootNode);
        ExtendedNode spaceDocumentsExtendedRootNode = mock(ExtendedNode.class);
        when(spaceNode.getNode(DocumentPermissionUpgradePlugin.ADMINISTRATORS_IDENTITY)).thenReturn(spaceDocumentsExtendedRootNode);
        spaceDocumentsExtendedRootNode.setPermission(DocumentPermissionUpgradePlugin.ADMINISTRATORS_IDENTITY, PermissionType.ALL);
        DocumentPermissionUpgradePlugin documentPermissionUpgradePlugin = new DocumentPermissionUpgradePlugin(initParams, nodeHierarchyCreator, repositoryService, sessionProviderService);
        documentPermissionUpgradePlugin.processUpgrade(null, null);
    }
}
