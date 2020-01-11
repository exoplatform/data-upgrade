package org.exoplatform.portal.jdbc.migration;

import java.util.Collections;

import javax.persistence.EntityTransaction;

import org.gatein.mop.api.workspace.*;
import org.gatein.mop.core.api.MOPService;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.component.test.*;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.pom.config.POMDataStorage;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.portal.pom.data.*;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.identity-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.migration-configuration.xml"),
})
public class TestSiteMigrationService extends AbstractJCRImplTest {

  private POMDataStorage       pomStorage;

  private ModelDataStorage     modelStorage;

  private POMSessionManager    manager;

  private SiteMigrationService siteMigrationService;

  @Override
  public void setUp() throws Exception {
    MigrationContext.resetForceStop();

    this.pomStorage = getService(POMDataStorage.class);
    this.modelStorage = getService(ModelDataStorage.class);
    this.manager = getService(POMSessionManager.class);
    this.siteMigrationService = getService(SiteMigrationService.class);

    begin();

    EntityManagerService managerService =
                                        getContainer().getComponentInstanceOfType(EntityManagerService.class);
    EntityTransaction transaction = managerService.getEntityManager().getTransaction();
    if (!transaction.isActive()) {
      transaction.begin();
    }
  }

  public void testMigrate() throws Exception {
    MOPService mop = manager.getPOMService();
    Site portal = mop.getModel().getWorkspace().addSite(ObjectType.PORTAL_SITE, "testSiteMigration");
    Navigation defaultNav = portal.getRootNavigation().addChild("default");
    defaultNav.addChild("a");

    portal.getRootPage().addChild("pages");
    portal.getRootPage().addChild("templates");

    ContainerData container = new ContainerData(null,
                                                null,
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                Collections.emptyList());
    PortalData portalData = new PortalData(null,
                                           "testSiteMigration",
                                           "portal",
                                           "en",
                                           "",
                                           "",
                                           Collections.emptyList(),
                                           "",
                                           null,
                                           "",
                                           container,
                                           Collections.emptyList());
    pomStorage.save(portalData);

    sync(true);

    siteMigrationService.doMigrate(new PortalKey(PortalConfig.PORTAL_TYPE, "testSiteMigration"));
    siteMigrationService.doRemove(new PortalKey(PortalConfig.PORTAL_TYPE, "testSiteMigration"));

    begin();

    assertNotNull(modelStorage.getPortalConfig(new PortalKey(PortalConfig.PORTAL_TYPE, "testSiteMigration")));
  }
}
