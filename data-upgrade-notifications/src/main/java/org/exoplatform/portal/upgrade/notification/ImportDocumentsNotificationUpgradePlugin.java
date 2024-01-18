package org.exoplatform.portal.upgrade.notification;

import java.util.List;
import java.util.Optional;

import org.exoplatform.commons.api.notification.model.PluginInfo;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.PluginSettingService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ImportDocumentsNotificationUpgradePlugin extends UpgradeProductPlugin {

  private static final Log           LOG                 = ExoLogger.getLogger(ImportDocumentsNotificationUpgradePlugin.class);

  private static final String        NOTIFICATION_PLUGIN = "notification.plugin.type";

  private final SettingService       settingService;

  private final UserSettingService   userSettingService;

  private final PluginSettingService pluginSettingService;

  private final EntityManagerService entityManagerService;

  private String                     notificationPlugin;

  private int settingsUpdateCount;

  public ImportDocumentsNotificationUpgradePlugin(SettingService settingService,
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
      notificationPlugin = initParams.getValueParam(NOTIFICATION_PLUGIN).getValue();
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion,
                                        String previousGroupVersion,
                                        UpgradePluginExecutionContext previousUpgradePluginExecution) {
    int executionCount = previousUpgradePluginExecution == null ? 0 : previousUpgradePluginExecution.getExecutionCount();
    return !isExecuteOnlyOnce() || executionCount == 0;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    if (notificationPlugin == null || notificationPlugin.isEmpty()) {
      LOG.error("Couldn't process upgrade, the parameter '{}' is mandatory", NOTIFICATION_PLUGIN);
      return;
    }
    ExoContainer currentContainer = ExoContainerContext.getCurrentContainer();
    int pageSize = 20;
    int current = 0;
    settingsUpdateCount = 0;
    try {
      LOG.info("=== Start initialisation of {} settings", notificationPlugin);
      LOG.info("  Starting activating {} Notifications for users", notificationPlugin);

      Optional<PluginInfo> optionalPluginTypeConfig =
                                                    pluginSettingService.getAllPlugins()
                                                                        .stream()
                                                                        .filter(pluginInfo -> pluginInfo.getType().equals(notificationPlugin))
                                                                        .findFirst();
      PluginInfo pluginTypeConfig;
      if (optionalPluginTypeConfig.isPresent()) {
        pluginTypeConfig = optionalPluginTypeConfig.get();
      } else {
        LOG.error("Couldn't process upgrade, the '{}' plugin is missing or not found", notificationPlugin);
        return;
      }
      List<String> usersContexts;
      entityManagerService.startRequest(currentContainer);
      long startTime = System.currentTimeMillis();
      do {
        LOG.info("  Progression of users {} Notifications settings initialisation : {} users", notificationPlugin, current);
        // Get all users who already update their notification settings
        usersContexts = settingService.getContextNamesByType(Context.USER.getName(), current, pageSize);
        if (usersContexts != null) {
          for (String userName : usersContexts) {
            try {
              entityManagerService.endRequest(currentContainer);
              entityManagerService.startRequest(currentContainer);
              UserSetting userSetting = this.userSettingService.get(userName);
              if (userSetting != null) {
                int initialSettingUpdateCount = settingsUpdateCount;
                updateSetting(userSetting, pluginTypeConfig);
                boolean isUserSettingsUpdated = initialSettingUpdateCount < settingsUpdateCount;
                // do not save the user settings if it isn't updated.
                if (isUserSettingsUpdated) {
                  userSettingService.save(userSetting);
                }
              }
            } catch (Exception e) {
              LOG.error("  Error while activating {} Notifications for user {} ", notificationPlugin, userName, e);
            }
          }
          current += usersContexts.size();
        }
        // Log the number of users' notification settings updated per page
        LOG.info(" Notifications settings initialized for : {} from {} users", settingsUpdateCount, current);
      } while (usersContexts != null && !usersContexts.isEmpty());
      long endTime = System.currentTimeMillis();
      // Log total number of users' notification settings updated.
      LOG.info("  Users {} Notifications settings initialised in {} ms", notificationPlugin, (endTime - startTime));
    } catch (Exception e) {
      LOG.error("Error while initialisation of users {} Notifications settings - Cause :", notificationPlugin, e.getMessage(), e);
    } finally {
      entityManagerService.endRequest(currentContainer);
    }
    LOG.info("=== {} users with modified notifications settings have been found and processed successfully", settingsUpdateCount);
    LOG.info("=== End initialisation of {} Notifications settings", notificationPlugin);
  }
  private void updateSetting(UserSetting userSetting, PluginInfo config) {
    boolean isSettingUpdated = false;
    for (String defaultConf : config.getDefaultConfig()) {
      for (String channelId : userSetting.getChannelActives()) {
        if (userSetting.getPlugins(channelId).contains(config.getType())) {
          continue;
        }
        if (UserSetting.FREQUENCY.getFrequecy(defaultConf) == UserSetting.FREQUENCY.INSTANTLY) {
          userSetting.addChannelPlugin(channelId, config.getType());
          isSettingUpdated = true;
        } else {
          userSetting.addPlugin(config.getType(), UserSetting.FREQUENCY.getFrequecy(defaultConf));
          isSettingUpdated = true;
        }
      }
    }
    if (isSettingUpdated) {
      settingsUpdateCount += 1;
    }
  }
}
