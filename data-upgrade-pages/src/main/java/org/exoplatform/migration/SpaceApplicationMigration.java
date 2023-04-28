package org.exoplatform.migration;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceException;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.Arrays;

public class SpaceApplicationMigration extends UpgradeProductPlugin {
  private static final Log    LOG          = ExoLogger.getExoLogger(SpaceApplicationMigration.class);

  private SpaceService        spaceService;

  private static final String OLD_APP_NAME = "old.app.name";

  private static final String OLD_APP_ID   = "old.app.id";

  private static final String NEW_APP_ID   = "new.app.id";

  private String              oldAppName;

  private String              oldAppId;

  private String              newAppId;

  public SpaceApplicationMigration(SpaceService spaceService, InitParams initParams) {
    super(initParams);
    this.spaceService = spaceService;
    oldAppName = initParams.getValueParam(OLD_APP_NAME).getValue();
    oldAppId = initParams.getValueParam(OLD_APP_ID).getValue();
    newAppId = initParams.getValueParam(NEW_APP_ID).getValue();
  }

  @Override
  public void processUpgrade(String s, String s1) {
    LOG.info("Start upgrade of space application: {}", OLD_APP_ID);
    long startupTime = System.currentTimeMillis();

    PortalContainer container = PortalContainer.getInstance();
    RequestLifeCycle.begin(container);
    try {
      SpaceFilter spaceFilter = new SpaceFilter();
      spaceFilter.setAppId(oldAppId);
      ListAccess<Space> spaces = spaceService.getAllSpacesByFilter(spaceFilter);
      Arrays.stream(spaces.load(0, spaces.getSize())).forEach(space -> {
        try {
          spaceService.removeApplication(space, oldAppId, oldAppName);
          spaceService.installApplication(space, newAppId);
          spaceService.activateApplication(space, newAppId);
        } catch (SpaceException e) {
          LOG.error("Error while removing old space application: {} and installing new space application: {}", oldAppId, newAppId, e);
        }
      });
    } catch (Exception e) {
      LOG.error("Error while getting spaces those contain the old space application {}", oldAppId, e);
    } finally {
      RequestLifeCycle.end();
    }
    LOG.info("End upgrade of space application: {}. It took {} ms", oldAppId, (System.currentTimeMillis() - startupTime));
  }
}
