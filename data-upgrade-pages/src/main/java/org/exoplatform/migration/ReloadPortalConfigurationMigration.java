package org.exoplatform.migration;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ReloadPortalConfigurationMigration extends UpgradeProductPlugin {
  private static final Log LOG = ExoLogger.getExoLogger(ReloadPortalConfigurationMigration.class);

  private UserPortalConfigService userPortalConfigService;

  private static final String OWNER_TYPE = "ownerType";
  private static final String PREDEFINED_OWNER   = "predefinedOwner";
  private static final String LOCATION_PARAMETER = "location";
  private static final String IMPORT_MODE        = "importMode";
  private static final String OVERRIDE_MODE = "overrideMode";

  String ownerType;
  String predefinedOwner;
  String location;
  String importMode;
  boolean overrideMode = false;
  public ReloadPortalConfigurationMigration(UserPortalConfigService userPortalConfigService, InitParams initParams) {
    super(initParams);

    if (initParams.containsKey(OWNER_TYPE) && !initParams.getValueParam(OWNER_TYPE).getValue().isBlank()) {
      ownerType=initParams.getValueParam(OWNER_TYPE).getValue();
    }
    if (initParams.containsKey(PREDEFINED_OWNER) && !initParams.getValueParam(PREDEFINED_OWNER).getValue().isBlank()) {
      predefinedOwner=initParams.getValueParam(PREDEFINED_OWNER).getValue();
    }
    if (initParams.containsKey(LOCATION_PARAMETER) && !initParams.getValueParam(LOCATION_PARAMETER).getValue().isBlank()) {
      location=initParams.getValueParam(LOCATION_PARAMETER).getValue();
    }
    if (initParams.containsKey(IMPORT_MODE) && !initParams.getValueParam(IMPORT_MODE).getValue().isBlank()) {
      importMode=initParams.getValueParam(IMPORT_MODE).getValue();
    }
    if (initParams.containsKey(OVERRIDE_MODE) && !initParams.getValueParam(OVERRIDE_MODE).getValue().isBlank()) {
      overrideMode=Boolean.parseBoolean(initParams.getValueParam(OVERRIDE_MODE).getValue());
    }
    this.userPortalConfigService = userPortalConfigService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    LOG.info("Start Upgrade ReloadPortalConfigurationMigration");
    if (ownerType!=null && predefinedOwner!=null && location!=null && importMode!=null) {
      userPortalConfigService.reloadConfig(ownerType,predefinedOwner,location,importMode,overrideMode);
    } else {
      LOG.error("At least one parameter is null : ownerType={}, predefinedOwner={}, location={}, importMode={}, overrideMode={}", ownerType,predefinedOwner,location,importMode,overrideMode);
      throw new RuntimeException("An error occurred while applying Upgrade Plugin ReloadPortalConfigurationMigration. Check plugin configuration");
    }
    LOG.info("End Upgrade ReloadPortalConfigurationMigration");

  }
}
