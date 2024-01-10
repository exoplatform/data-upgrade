package org.exoplatform.wiki.upgrade;

import static org.exoplatform.services.jcr.impl.Constants.EXO_PRIVILEGEABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

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

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.impl.core.query.QueryImpl;

@RunWith(MockitoJUnitRunner.class)
public class NotesFolderPermissionsUpgradePluginTest {

  @Mock
  RepositoryService      repositoryService;

  @Mock
  ManageableRepository   repository;

  @Mock
  RepositoryEntry        repositoryEntry;

  @Mock
  Session                session;

  @Mock
  SessionProviderService sessionProviderService;

  @Mock
  SessionProvider        sessionProvider;

  @Test
  public void NotesFolderPermissionsUpgradePluginTest() throws Exception {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.news");

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
    ExtendedNode notesNode = mock(ExtendedNode.class);
    when(notesNode.getPath()).thenReturn("Group/spaces/test/notes");
    when(nodeIterator.nextNode()).thenReturn(notesNode);
    AccessControlList accessControlList = mock(AccessControlList.class);
    when(notesNode.getACL()).thenReturn(accessControlList);
    AccessControlEntry accessControlEntry = new AccessControlEntry("/spaces/test", "read");
    when(accessControlList.getPermissionEntries()).thenReturn(List.of(accessControlEntry));
    NodeIterator imagesNodeIterator = mock(NodeIterator.class);
    when(notesNode.getNodes()).thenReturn(imagesNodeIterator);
    when(imagesNodeIterator.hasNext()).thenReturn(true, false);
    ExtendedNode imagesNode = mock(ExtendedNode.class);
    when(imagesNodeIterator.nextNode()).thenReturn(imagesNode);
    when(imagesNode.getPath()).thenReturn("Group/spaces/test/notes/images");
    when(imagesNode.getNodes()).thenReturn(imagesNodeIterator);
    when(imagesNode.getACL()).thenReturn(accessControlList);

    // when
    NotesFolderPermissionsUpgradePlugin notesFolderPermissionsUpgradePlugin =
                                                                            new NotesFolderPermissionsUpgradePlugin(initParams,
                                                                                                                    repositoryService,
                                                                                                                    sessionProviderService);
    notesFolderPermissionsUpgradePlugin.processUpgrade(null, null);
    // then
    verify(notesNode, times(1)).removePermission("/spaces/test");
    verify(notesNode,
           times(1)).setPermission("*:/spaces/test",
                                   new String[] { PermissionType.READ, PermissionType.ADD_NODE, PermissionType.SET_PROPERTY });
    verify(notesNode, times(1)).save();

    verify(imagesNode, times(1)).removePermission("/spaces/test");
    verify(imagesNode,
            times(1)).setPermission("*:/spaces/test",
            new String[] { PermissionType.READ, PermissionType.ADD_NODE, PermissionType.SET_PROPERTY });
    verify(imagesNode, times(1)).save();
    //
    accessControlEntry = new AccessControlEntry("*:/spaces/test", "read");
    when(accessControlList.getPermissionEntries()).thenReturn(List.of(accessControlEntry));
    when(nodeIterator.hasNext()).thenReturn(true, false);
    notesFolderPermissionsUpgradePlugin.processUpgrade(null, null);
    // then
    // no invocation with the correct permission
    verify(notesNode,
            atLeast(0)).setPermission("*:/spaces/test",
            new String[] { PermissionType.READ, PermissionType.ADD_NODE, PermissionType.SET_PROPERTY });
    verify(notesNode, atLeast(0)).save();


  }
}
