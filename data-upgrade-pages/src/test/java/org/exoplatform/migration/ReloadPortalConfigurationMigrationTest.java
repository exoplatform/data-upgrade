package org.exoplatform.migration;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserPortalConfigService;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml"),
})
@RunWith(MockitoJUnitRunner.class)
public class ReloadPortalConfigurationMigrationTest extends AbstractKernelTest {

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
