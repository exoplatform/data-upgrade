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

import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.space.model.Space;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AddPublishersPermissionsUpgradePluginTest {

  @Mock
  private SpaceService                          spaceService;

  @Mock
  private SessionProviderService                sessionProviderService;

  @Mock
  private RepositoryService                     repositoryService;

  @Mock
  private EntityManagerService                  entityManagerService;

  @Mock
  private SettingService                        settingService;

  @Mock
  private NodeHierarchyCreator                  nodeHierarchyCreator;

  @Mock
  private ManageableRepository                  repository;

  @Mock
  private RepositoryEntry                       repositoryEntry;

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
                                                                                      entityManagerService,
                                                                                      nodeHierarchyCreator,
                                                                                      settingService));
  }

  @Test
  public void testProcessUpgrade_successfulMigration() throws Exception {
    // Mock space
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    when(spaceService.getSpaceById("1")).thenReturn(space);

    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    SessionProvider sessionProvider = mock(SessionProvider.class);
    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    Session session = mock(Session.class);
    when(sessionProvider.getSession(anyString(), any(ManageableRepository.class))).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    lenient().when(session.getWorkspace()).thenReturn(workspace);

    when(nodeHierarchyCreator.getJcrPath(AddPublishersPermissionsUpgradePlugin.GROUPS_PATH_ALIAS))
            .thenReturn("/Groups/");
    when(session.itemExists(anyString())).thenReturn(true);
    when(session.getItem(anyString())).thenReturn(extendedNode);

    NodeType nodeType = mock(NodeType.class);
    when(nodeType.getName()).thenReturn("nt:file");
    when(extendedNode.getPrimaryNodeType()).thenReturn(nodeType);
    NodeIterator nodeIterator = mock(NodeIterator.class);
    when(nodeIterator.hasNext()).thenReturn(true, false);
    when(nodeIterator.nextNode()).thenReturn(extendedNode);
    when(extendedNode.getNodes()).thenReturn(nodeIterator);

    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    // Run the processUpgrade method
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(extendedNode).setPermission("publisher:/platform/users", PermissionType.ALL);
    verify(extendedNode).save();
    verify(session).save();
  }

  @Test
  public void testProcessUpgrade_noSpaces() {
    // No spaces to migrate
    lenient().doReturn(Collections.emptyList()).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    // Run the processUpgrade method
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(spaceService, never()).getSpaceById(anyString());
  }

  @Test
  public void shouldProceedToUpgrade() {
    SettingValue settingValue = mock(SettingValue.class);
    UpgradePluginExecutionContext context = new UpgradePluginExecutionContext("0.9", 1);
    when(settingService.get(any(), any(), anyString())).thenReturn(null);
    addPublishersPermissionsUpgradePlugin.shouldProceedToUpgrade("0.9", "1.0", context);
    verify(settingService, times(1)).set(any(), any(), anyString(), any());
    reset(settingService);
    when(settingService.get(any(), any(), anyString())).thenReturn(settingValue);
    context.setVersion("1.2");
    addPublishersPermissionsUpgradePlugin.shouldProceedToUpgrade("1.2", "1.0", context);
    verify(settingService, times(0)).set(any(), any(), anyString(), any());
  }
}