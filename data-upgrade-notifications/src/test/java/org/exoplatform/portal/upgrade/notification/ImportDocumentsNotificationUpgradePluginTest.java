package org.exoplatform.portal.upgrade.notification;

import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.api.notification.model.PluginInfo;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

@RunWith(MockitoJUnitRunner.class)
public class ImportDocumentsNotificationUpgradePluginTest {

  private static final MockedStatic<ExoContainerContext> EXO_CONTAINER_CONTEXT = mockStatic(ExoContainerContext.class);

  private static final MockedStatic<PortalContainer>     PORTAL_CONTAINER      = mockStatic(PortalContainer.class);

  @Mock
  private EntityManagerService                           entityManagerService;

  @Mock
  private SettingService                                 settingService;

  @Mock
  private UserSettingService                             userSettingService;

  @Mock
  private PluginSettingService                           pluginSettingService;

  @AfterClass
  public static void afterRunBare() throws Exception { // NOSONAR
    EXO_CONTAINER_CONTEXT.close();
    PORTAL_CONTAINER.close();
  }

  @Test
  public void processUpgrade() throws Exception {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    valueParam.setName("notification.plugin.type");
    valueParam.setValue("ImportDocumentsPlugin");
    initParams.addParam(valueParam);
    PortalContainer container = mock(PortalContainer.class);
    EXO_CONTAINER_CONTEXT.when(() -> ExoContainerContext.getCurrentContainer()).thenReturn(container);
    PluginInfo pluginTypeConfig = mock(PluginInfo.class);
    when(pluginSettingService.getAllPlugins()).thenReturn(Collections.singletonList(pluginTypeConfig));
    when(settingService.getContextNamesByType(Context.USER.getName(), 0, 20)).thenReturn(List.of("userTest"));
    UserSetting userSetting = mock(UserSetting.class);
    Set<String> channelActives = new HashSet<>();
    channelActives.add("MAIL_CHANNEL");
    when(userSetting.getChannelActives()).thenReturn(channelActives);
    when(userSettingService.get("userTest")).thenReturn(userSetting);
    when(pluginTypeConfig.getDefaultConfig()).thenReturn(Arrays.asList("daily", "Instantly"));
    when(pluginTypeConfig.getType()).thenReturn("ImportDocumentsPlugin");
    ImportDocumentsNotificationUpgradePlugin notificationUpgradePlugin =
                                                                       new ImportDocumentsNotificationUpgradePlugin(settingService,
                                                                                                                    userSettingService,
                                                                                                                    pluginSettingService,
                                                                                                                    entityManagerService,
                                                                                                                    initParams);
    notificationUpgradePlugin.processUpgrade(null, null);
    //
    verify(userSetting, times(1)).addChannelPlugin("MAIL_CHANNEL", pluginTypeConfig.getType());
    verify(userSetting, times(1)).addPlugin(pluginTypeConfig.getType(), UserSetting.FREQUENCY.getFrequecy("daily"));
    verify(userSettingService, times(1)).save(userSetting);
  }
}
