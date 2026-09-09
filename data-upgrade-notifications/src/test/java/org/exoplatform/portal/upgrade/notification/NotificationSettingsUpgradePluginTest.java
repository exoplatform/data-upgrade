/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.portal.upgrade.notification;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.notification.model.PluginInfo;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

@RunWith(MockitoJUnitRunner.Silent.class)
public class NotificationSettingsUpgradePluginTest {

  private static final String                PLUGIN_TYPE = "PostReportPlugin";

  @Mock
  private SettingService                     settingService;

  @Mock
  private UserSettingService                 userSettingService;

  @Mock
  private PluginSettingService               pluginSettingService;

  @Mock
  private EntityManagerService               entityManagerService;

  private NotificationSettingsUpgradePlugin  upgradePlugin;

  @Before
  public void setUp() {
    upgradePlugin = new NotificationSettingsUpgradePlugin(settingService,
                                                          userSettingService,
                                                          pluginSettingService,
                                                          entityManagerService,
                                                          initParams());
  }

  @Test
  public void testMissingPluginAbortsWithoutTouchingUsers() {
    // the plugin type is not registered: the upgrade must fail loudly so the
    // framework stores no execution and retries on next startup, instead of
    // burning its execute-once on a run that could not do its job
    when(pluginSettingService.getAllPlugins()).thenReturn(Collections.emptyList());

    assertThrows(IllegalStateException.class, () -> upgradePlugin.processUpgrade("0", "7.3.0"));

    verify(userSettingService, never()).save(any());
  }

  @Test
  public void testActivatesPluginOnEveryActiveChannelOfStoredSettings() {
    PluginInfo pluginInfo = new PluginInfo();
    pluginInfo.setType(PLUGIN_TYPE);
    pluginInfo.setDefaultConfig(List.of("Instantly"));
    when(pluginSettingService.getAllPlugins()).thenReturn(List.of(pluginInfo));

    when(settingService.getContextNamesByType(anyString(), anyInt(), anyInt())).thenReturn(List.of("admin"))
                                                                               .thenReturn(Collections.emptyList());
    UserSetting userSetting = UserSetting.getInstance();
    userSetting.setUserId("admin");
    userSetting.setChannelActives(new HashSet<>(List.of("MAIL_CHANNEL", "WEB_CHANNEL")));
    when(userSettingService.get("admin")).thenReturn(userSetting);

    upgradePlugin.processUpgrade("0", "7.3.0");

    assertTrue("The plugin must be activated on the user's mail channel",
               userSetting.getPlugins("MAIL_CHANNEL").contains(PLUGIN_TYPE));
    assertTrue("The plugin must be activated on the user's web channel",
               userSetting.getPlugins("WEB_CHANNEL").contains(PLUGIN_TYPE));
    verify(userSettingService).save(userSetting);
  }

  private InitParams initParams() {
    InitParams initParams = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.platform");
    initParams.addParameter(groupId);
    ValueParam types = new ValueParam();
    types.setName("notification.upgrade.settings.plugin.types");
    types.setValue(PLUGIN_TYPE);
    initParams.addParameter(types);
    return initParams;
  }

}
