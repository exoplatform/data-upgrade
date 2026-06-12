/*
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
package org.exoplatform.migration;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import javax.jcr.NodeIterator;
import javax.jcr.Session;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.class)
public class AddPublishersPermissionsUpgradePluginTest {

  @Mock
  private SpaceService                          spaceService;

  @Mock
  private SessionProviderService                sessionProviderService;

  @Mock
  private RepositoryService                     repositoryService;

  @Mock
  private SettingService                        settingService;

  @Mock
  private NodeHierarchyCreator                  nodeHierarchyCreator;

  @Mock
  private ManageableRepository                  repository;

  @Mock
  private ExtendedNode                          extendedNode;

  private AddPublishersPermissionsUpgradePlugin addPublishersPermissionsUpgradePlugin;

  @Before
  public void setUp() {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    addPublishersPermissionsUpgradePlugin = spy(new AddPublishersPermissionsUpgradePlugin(initParams,
                                                                                       spaceService,
                                                                                       sessionProviderService,
                                                                                       repositoryService,
                                                                                       null,
                                                                                       nodeHierarchyCreator,
                                                                                       settingService));
  }

  private Session mockSession() throws Exception {
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    Session session = mock(Session.class);
    when(sessionProvider.getSession(anyString(), any(ManageableRepository.class))).thenReturn(session);
    return session;
  }

  private void mockRootNodeWithChildren(Session session, boolean privilegeable, boolean hasPublisher) throws Exception {
    lenient().when(nodeHierarchyCreator.getJcrPath(AddPublishersPermissionsUpgradePlugin.GROUPS_PATH_ALIAS)).thenReturn("/Groups/");
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(anyString())).thenReturn(extendedNode);

    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(extendedNode);
    when(extendedNode.getNodes()).thenReturn(nodeIterator);

    when(extendedNode.isNodeType("exo:privilegeable")).thenReturn(privilegeable);
    if (privilegeable) {
      AccessControlEntry managerEntry = new AccessControlEntry("manager:/platform/users", "read");
      AccessControlEntry redactorEntry = new AccessControlEntry("redactor:/platform/users", "read");
      List<AccessControlEntry> aclEntries;
      if (hasPublisher) {
        aclEntries = List.of(managerEntry, redactorEntry,
                             new AccessControlEntry("publisher:/platform/users", "read"));
      } else {
        aclEntries = List.of(managerEntry, redactorEntry);
      }
      when(extendedNode.getACL()).thenReturn(new AccessControlList("root", aclEntries));
    }
  }

  @Test
  public void testProcessUpgrade_successfulMigration() throws Exception {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    when(spaceService.getSpaceById("1")).thenReturn(space);

    Session session = mockSession();
    mockRootNodeWithChildren(session, true, false);

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(extendedNode).setPermission("publisher:/platform/users", PermissionType.ALL);
    verify(extendedNode).save();
    verify(session).save();
  }

  @Test
  public void testProcessUpgrade_noSpaces() throws Exception {
    mockSession();
    doReturn(Collections.emptyList()).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    verify(spaceService, never()).getSpaceById(anyString());
  }

  @Test
  public void testProcessUpgrade_spaceRootNodeIsNull() throws Exception {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    when(spaceService.getSpaceById("1")).thenReturn(space);

    Session session = mockSession();
    lenient().when(nodeHierarchyCreator.getJcrPath(AddPublishersPermissionsUpgradePlugin.GROUPS_PATH_ALIAS)).thenReturn("/Groups/");
    when(session.itemExists(anyString())).thenReturn(false);

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    verify(session, never()).save();
  }

  @Test
  public void testProcessUpgrade_nodeAlreadyHasPublisher() throws Exception {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    when(spaceService.getSpaceById("1")).thenReturn(space);

    Session session = mockSession();
    mockRootNodeWithChildren(session, true, true);

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(extendedNode, never()).setPermission(anyString(), any(String[].class));
    verify(session).save();
  }

  @Test
  public void testProcessUpgrade_nonPrivilegeableNode() throws Exception {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    when(spaceService.getSpaceById("1")).thenReturn(space);

    Session session = mockSession();
    mockRootNodeWithChildren(session, false, false);

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(extendedNode, never()).setPermission(anyString(), any(String[].class));
    verify(session).save();
  }

  @Test
  public void testProcessUpgrade_resumeFromCheckpoint() throws Exception {
    when(settingService.get(any(), any(), anyString())).thenAnswer(invocation -> SettingValue.create("2"));

    Space space1 = new Space();
    space1.setId("1");
    space1.setGroupId("/platform/users");
    space1.setPrettyName("Space1");
    when(spaceService.getSpaceById("1")).thenReturn(space1);

    mockSession();
    doReturn(List.of(1L, 2L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(spaceService, never()).getSpaceById("2");
    verify(spaceService, times(1)).getSpaceById("1");
  }

  @Test
  public void testProcessUpgrade_someSpacesFail() throws Exception {
    mockSession();
    when(spaceService.getSpaceById("1")).thenThrow(new RuntimeException("space error"));

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    assertThrows(IllegalStateException.class,
                 () -> addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0"));
  }

  @Test
  public void testProcessUpgrade_errorGettingRepository() throws Exception {
    lenient().when(repositoryService.getCurrentRepository()).thenThrow(new RuntimeException("DB error"));
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    verify(settingService, never()).set(any(), any(), any(), any());
  }

  @Test
  public void testAfterUpgrade_whenUpgradeFailed() throws Exception {
    lenient().when(repositoryService.getCurrentRepository()).thenThrow(new RuntimeException("error"));
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    addPublishersPermissionsUpgradePlugin.afterUpgrade();
    verify(settingService, never()).set(any(), any(), any(), any());
  }

  @Test
  public void testAfterUpgrade_whenUpgradeSucceeded() throws Exception {
    mockSession();
    doReturn(Collections.emptyList()).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    addPublishersPermissionsUpgradePlugin.afterUpgrade();
    verify(settingService, times(1)).set(any(), any(), anyString(), any());
  }

  @Test
  public void shouldProceedToUpgrade_settingNotSet() {
    when(settingService.get(any(), any(), anyString())).thenReturn(null);
    UpgradePluginExecutionContext context = new UpgradePluginExecutionContext("0.9", 1);
    addPublishersPermissionsUpgradePlugin.shouldProceedToUpgrade("0.9", "1.0", context);
    verify(settingService, times(1)).set(any(), any(), anyString(), any());
  }

  @Test
  public void shouldProceedToUpgrade_settingAlreadySet() {
    when(settingService.get(any(), any(), anyString())).thenReturn(mock(SettingValue.class));
    UpgradePluginExecutionContext context = new UpgradePluginExecutionContext("1.2", 1);
    addPublishersPermissionsUpgradePlugin.shouldProceedToUpgrade("1.2", "1.0", context);
    verify(settingService, never()).set(any(), any(), anyString(), any());
  }
}
