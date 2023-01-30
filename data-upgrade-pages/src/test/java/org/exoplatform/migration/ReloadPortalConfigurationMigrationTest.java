package org.exoplatform.migration;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class ReloadPortalConfigurationMigrationTest {

  @Mock
  UserPortalConfigService userPortalConfigService;

  @Test
  public void testProcessUpgrade() {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    String ownerType = "portal";
    valueParam.setName("ownerType");
    valueParam.setValue(ownerType);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    String predefinedOwner="global";
    valueParam.setName("predefinedOwner");
    valueParam.setValue(predefinedOwner);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    String location = "war:/conf/extension/portal";
    valueParam.setName("location");
    valueParam.setValue(location);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("importMode");
    String importMode="merge";
    valueParam.setValue(importMode);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("overrideMode");
    String overrideMode="true";

    valueParam.setValue(overrideMode);
    initParams.addParameter(valueParam);

    ReloadPortalConfigurationMigration reloadPortalConfigurationMigration = new ReloadPortalConfigurationMigration(userPortalConfigService,initParams);

    reloadPortalConfigurationMigration.processUpgrade("v1","v2");
    Mockito.verify(userPortalConfigService,times(1)).reloadConfig(ownerType,predefinedOwner,location,importMode,Boolean.parseBoolean(overrideMode));

  }

  @Test
  public void testFailProcessUpgrade() {
    InitParams initParams = new InitParams();


    ReloadPortalConfigurationMigration reloadPortalConfigurationMigration = new ReloadPortalConfigurationMigration(userPortalConfigService,initParams);
    try {
      reloadPortalConfigurationMigration.processUpgrade("v1", "v2");
      fail("Upgrade Plugin should not execute due to missing parameters");
    } catch (RuntimeException re) {
      //normal exception in this case
    }
    Mockito.verify(userPortalConfigService,times(0)).reloadConfig(anyString(),anyString(),anyString(),anyString(),anyBoolean());

  }
}
