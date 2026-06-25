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
package org.exoplatform.upgrade;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.documents.service.DocumentFileService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

@RunWith(MockitoJUnitRunner.class)
public class AddPublishersPermissionsUpgradePluginTest {

  @Mock
  private SpaceService                          spaceService;

  @Mock
  private DocumentFileService                   documentFileService;

  @Mock
  private SettingService                        settingService;

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
                                                                                         documentFileService,
                                                                                         null,
                                                                                         settingService));
  }

  private void mockSpaceForMigration(Space space) {
    when(spaceService.getSpaceById(space.getId())).thenReturn(space);
  }

  @Test
  public void testProcessUpgrade_successfulMigration() {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    mockSpaceForMigration(space);
    doReturn(Collections.singletonList(1L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(documentFileService).synchronizeSpacePermissions(space);
    verify(settingService).set(any(), any(), anyString(), any());
  }

  @Test
  public void testProcessUpgrade_noSpaces() {
    doReturn(Collections.emptyList()).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");
    verify(spaceService, never()).getSpaceById(anyString());
  }

  @Test
  public void testProcessUpgrade_spaceError_continues() {
    Space space = new Space();
    space.setId("1");
    space.setGroupId("/platform/users");
    space.setPrettyName("TestSpace");
    mockSpaceForMigration(space);
    doThrow(new RuntimeException("migration error")).when(documentFileService).synchronizeSpacePermissions(space);

    Space space2 = new Space();
    space2.setId("2");
    space2.setGroupId("/platform/users");
    space2.setPrettyName("TestSpace2");
    mockSpaceForMigration(space2);

    doReturn(List.of(1L, 2L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    assertThrows(Exception.class,
                 () -> addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0"));

    verify(documentFileService).synchronizeSpacePermissions(space);
    verify(documentFileService).synchronizeSpacePermissions(space2);
  }

  @Test
  public void testProcessUpgrade_resumeFromCheckpoint() {
    when(settingService.get(any(), any(), anyString())).thenAnswer(invocation -> SettingValue.create("2"));

    Space space1 = new Space();
    space1.setId("1");
    space1.setGroupId("/platform/users");
    space1.setPrettyName("Space1");
    mockSpaceForMigration(space1);

    doReturn(List.of(1L, 2L)).when(addPublishersPermissionsUpgradePlugin).getRedactionalSpaces();
    addPublishersPermissionsUpgradePlugin.processUpgrade("1.0", "2.0");

    verify(spaceService, never()).getSpaceById("2");
    verify(documentFileService).synchronizeSpacePermissions(space1);
  }
}
