/**
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.upgrade;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.security.IdentityConstants;

import lombok.SneakyThrows;

@RunWith(MockitoJUnitRunner.class)
public class RemoveAnyPermissionUpgradePluginTest {

  @Mock
  private RepositoryService                repositoryService;

  @Mock
  private ManageableRepository             repository;

  @Mock
  private RepositoryEntry                  repositoryEntry;

  @Mock
  private Session                          systemSession;

  @Mock
  private Session                          anonymousSession;

  @Mock
  private Workspace                        workspace;

  @Mock
  private QueryManager                     queryManager;

  @Mock
  private Query                            query;

  @Mock
  private QueryResult                      queryResult;

  @Mock
  private NodeIterator                     nodeIterator;

  private RemoveAnyPermissionUpgradePlugin plugin;

  @Before
  @SneakyThrows
  public void setUp() {
    plugin = spy(new RemoveAnyPermissionUpgradePlugin(new InitParams(), repositoryService));

    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getSystemSession(anyString())).thenReturn(systemSession);
    when(repository.getDynamicSession(anyString(), anyList())).thenReturn(anonymousSession);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("test");

    when(anonymousSession.getWorkspace()).thenReturn(workspace);
    when(workspace.getQueryManager()).thenReturn(queryManager);
    when(queryManager.createQuery(anyString(), eq(Query.SQL))).thenReturn(query);
    when(query.execute()).thenReturn(queryResult);
    when(queryResult.getNodes()).thenReturn(nodeIterator);
  }

  @Test
  @SneakyThrows
  public void testProcessUpgradeSuccess() {
    ExtendedNode node = mockNode("/Users/john/doc1", true);

    when(nodeIterator.hasNext()).thenReturn(true)
                                .thenReturn(false)
                                .thenReturn(false)
                                .thenReturn(false);

    plugin.processUpgrade("1.0", "2.0");

    verify(node).removePermission(IdentityConstants.ANY);
    verify(node).save();
  }

  @Test
  @SneakyThrows
  public void testSkipNodeWithoutAnyPermission() {
    ExtendedNode node = mockNode("/Users/john/doc1", false);

    when(nodeIterator.hasNext()).thenReturn(true)
                                .thenReturn(false)
                                .thenReturn(false)
                                .thenReturn(false);

    plugin.processUpgrade("1.0", "2.0");

    verify(node, never()).removePermission(anyString());
    verify(node, never()).save();
  }

  @Test
  @SneakyThrows
  public void testFailsWhenAnonymousAccessRemains() {
    when(nodeIterator.hasNext()).thenReturn(false)
                                .thenReturn(true);

    assertThrows(Exception.class,
                 () -> plugin.processUpgrade("1.0", "2.0"));
  }

  @SneakyThrows
  private ExtendedNode mockNode(String path, boolean anyPermission) {
    Node anonymousNode = mock(Node.class);
    when(anonymousNode.getPath()).thenReturn(path);

    when(nodeIterator.nextNode()).thenReturn(anonymousNode);

    ExtendedNode node = mock(ExtendedNode.class);
    when(systemSession.getItem(path)).thenReturn(node);

    AccessControlList acl = mock(AccessControlList.class);

    if (anyPermission) {
      AccessControlEntry ace = mock(AccessControlEntry.class);
      when(ace.getIdentity()).thenReturn(IdentityConstants.ANY);
      when(acl.getPermissionEntries()).thenReturn(Collections.singletonList(ace));
    } else {
      when(acl.getPermissionEntries()).thenReturn(Collections.emptyList());
    }

    when(node.getACL()).thenReturn(acl);

    return node;
  }
}
