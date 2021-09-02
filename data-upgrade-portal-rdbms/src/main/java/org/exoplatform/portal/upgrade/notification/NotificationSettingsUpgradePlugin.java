package org.exoplatform.portal.upgrade.notification;

import org.exoplatform.commons.api.notification.model.PluginInfo;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationSettingsUpgradePlugin extends UpgradeProductPlugin {
  private static final Log     LOG                 = ExoLogger.getLogger(NotificationSettingsUpgradePlugin.class);

  private static final String  NOTIFICATION_PLUGIN = "notification.upgrade.settings.plugin";

  private SettingService       settingService;

  private UserSettingService   userSettingService;

  private PluginSettingService pluginSettingService;

  private EntityManagerService entityManagerService;

  private String               notificationPluginTypes;

  public NotificationSettingsUpgradePlugin(SettingService settingService,
                                           UserSettingService userSettingService,
                                           PluginSettingService pluginSettingService,
                                           EntityManagerService entityManagerService,
                                           InitParams initParams) {
    super(settingService, initParams);
    this.settingService = settingService;
    this.userSettingService = userSettingService;
    this.pluginSettingService = pluginSettingService;
    this.entityManagerService = entityManagerService;
    if (initParams.containsKey(NOTIFICATION_PLUGIN)) {
      notificationPluginTypes = initParams.getValueParam(NOTIFICATION_PLUGIN).getValue();
    }
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    List<String>    pluginTypes = Arrays.asList(notificationPluginTypes.replace("\n", "").replaceAll("\\s", "").split(","));
    
    for (String pluginType : pluginTypes) {
      int pageSize = 20;
      int current = 0;
      try {
        LOG.info("=== Start initialisation of {} settings", pluginType);
        LOG.info("  Starting activating {} Notifications for users", pluginType);

        PluginInfo pluginTypeConfig = findPlugin(pluginType);
        List<String> usersContexts;

        entityManagerService.startRequest(currentContainer);
        long startTime = System.currentTimeMillis();
        do {
          LOG.info("  Progression of users {} Notifications settings initialisation : {} users", pluginType, current);

          // Get all users who already update their notification settings
          usersContexts = settingService.getContextNamesByType(Context.USER.getName(), current, pageSize);

          if (usersContexts != null) {
            for (String userName : usersContexts) {
              try {
                entityManagerService.endRequest(currentContainer);
                entityManagerService.startRequest(currentContainer);

                UserSetting userSetting = this.userSettingService.get(userName);
                if (userSetting != null) {
                  updateSetting(userSetting, pluginTypeConfig);
                  userSettingService.save(userSetting);
                }
              } catch (Exception e) {
                LOG.error("  Error while activating {} Notifications for user {} ", pluginType, userName, e);
              }
            }
            current += usersContexts.size();
          }
        } while (usersContexts != null && !usersContexts.isEmpty());
        long endTime = System.currentTimeMillis();
        LOG.info("  Users {} Notifications settings initialised in {} ms", pluginType, (endTime - startTime));
      } catch (Exception e) {
        LOG.error("Error while initialisation of users {} Notifications settings - Cause :", pluginType, e.getMessage(), e);
      } finally {
        entityManagerService.endRequest(currentContainer);
      }

      LOG.info("=== {} users with modified notifications settings have been found and processed successfully", current);
      LOG.info("=== End initialisation of {} Notifications settings", pluginType);
    }
  }

  private PluginInfo findPlugin(String type) {
    for (PluginInfo plugin : pluginSettingService.getAllPlugins()) {
      if (plugin.getType().equals(type)) {
        return plugin;
      }
    }
    return null;
  }

  private void updateSetting(UserSetting userSetting, PluginInfo config) {
    for (String defaultConf : config.getDefaultConfig()) {
      for (String channelId : config.getAllChannelActive()) {
        if (UserSetting.FREQUENCY.getFrequecy(defaultConf) == UserSetting.FREQUENCY.INSTANTLY) {
          userSetting.addChannelPlugin(channelId, config.getType());
        } else {
          userSetting.addPlugin(config.getType(), UserSetting.FREQUENCY.getFrequecy(defaultConf));
        }
      }
    }
  }
}
