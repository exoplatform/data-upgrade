package org.exoplatform.migration;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceException;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpaceApplicationMigration extends UpgradeProductPlugin {
  private static final Log     LOG                = ExoLogger.getExoLogger(SpaceApplicationMigration.class);

  private SpaceService         spaceService;

  private EntityManagerService entityManagerService;

  private static final String  OLD_APP_NAME       = "old.app.name";

  private static final String  OLD_APP_ID         = "old.app.id";

  private static final String  NEW_APP_ID         = "new.app.id";

  private String               oldAppName;

  private String               oldAppId;

  private String               newAppId;

  private boolean              oldSpaceAppRemoved = false;

  public SpaceApplicationMigration(SpaceService spaceService, EntityManagerService entityManagerService, InitParams initParams) {
    super(initParams);
    this.spaceService = spaceService;
    this.entityManagerService = entityManagerService;
    oldAppName = initParams.getValueParam(OLD_APP_NAME).getValue();
    oldAppId = initParams.getValueParam(OLD_APP_ID).getValue();
    newAppId = initParams.getValueParam(NEW_APP_ID).getValue();
  }

  @Override
  public void processUpgrade(String s, String s1) {
    LOG.info("Start upgrade of space application: {}", oldAppId);
    long startupTime = System.currentTimeMillis();

    PortalContainer container = PortalContainer.getInstance();
    RequestLifeCycle.begin(container);
    int updatedSpaces = 0;
    try {
      SpaceFilter spaceFilter = new SpaceFilter();
      spaceFilter.setAppId(oldAppId);
      ListAccess<Space> spaces = spaceService.getAllSpacesByFilter(spaceFilter);
      Space[] loadedSpaces = spaces.load(0, spaces.getSize());
      updatedSpaces = loadedSpaces.length;
      Arrays.stream(loadedSpaces).forEach(space -> {
        try {
          forceRemoveOldApplication(space.getGroupId());
          removeApp(space, oldAppId, oldAppName);
          if (!SpaceUtils.isInstalledApp(space, newAppId)) {
            spaceService.installApplication(space, newAppId);
          }
          spaceService.activateApplication(space, newAppId);
          LOG.info("Space application: {} of space: {} has been updated", newAppId, space.getDisplayName());
        } catch (SpaceException e) {
          LOG.error("Error while removing old space application: {} and installing new space application: {}",
                    oldAppId,
                    newAppId,
                    e);
        }
      });
    } catch (Exception e) {
      LOG.error("Error while getting spaces those contain the old space application {}", oldAppId, e);
    } finally {
      RequestLifeCycle.end();
    }
    LOG.info("End upgrade of space application: {}. {} spaces has been updated. It took {} ms",
             oldAppId,
             updatedSpaces,
             (System.currentTimeMillis() - startupTime));
  }

  private void forceRemoveOldApplication(String spaceGroupId) {
    LOG.info("Remove old space application: {}", oldAppId);
    boolean transactionStarted = false;
    EntityManager entityManager = this.entityManagerService.getEntityManager();
    try {
      if (!entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().begin();
        transactionStarted = true;
      }

      String sqlString = "DELETE FROM PORTAL_PAGES WHERE SITE_ID IN (SELECT ID FROM PORTAL_SITES WHERE NAME ='" + spaceGroupId
          + "') AND NAME='" + oldAppId + "'";
      Query nativeQuery = entityManager.createNativeQuery(sqlString);
      nativeQuery.executeUpdate();
      if (!oldSpaceAppRemoved) {
        String sqlString1 = "DELETE FROM SOC_APPS WHERE APP_ID='" + oldAppId + "'";
        String sqlString2 = "DELETE FROM PORTAL_APPLICATIONS WHERE APP_NAME='" + oldAppId + "'";
        Query nativeQuery1 = entityManager.createNativeQuery(sqlString1);
        Query nativeQuery2 = entityManager.createNativeQuery(sqlString2);
        nativeQuery1.executeUpdate();
        nativeQuery2.executeUpdate();
        oldSpaceAppRemoved = true;
      }
      if (transactionStarted && entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().commit();
      }
    } catch (Exception e) {
      if (transactionStarted && entityManager.getTransaction().isActive() && entityManager.getTransaction().getRollbackOnly()) {
        entityManager.getTransaction().rollback();
      }
    }
    LOG.info("End remove old space application: {}", oldAppId);
  }

  private void removeApp(Space space, String appId, String appName) throws SpaceException {
    String[] array = space.getApp().split(appId + ":+" + appName + ":(true|false):active,");
    String newApp;
    if (array.length == 2) {
      newApp = array[0] + array[1];
    } else {
      newApp = array[0];
    }
    space.setApp(newApp);
    this.spaceService.updateSpace(space);
  }

}
