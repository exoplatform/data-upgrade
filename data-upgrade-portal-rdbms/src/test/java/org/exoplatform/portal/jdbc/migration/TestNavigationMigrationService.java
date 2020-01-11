package org.exoplatform.portal.jdbc.migration;

import java.util.*;

import org.gatein.mop.api.workspace.*;
import org.gatein.mop.core.api.MOPService;

import org.exoplatform.component.test.*;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.description.DescriptionService;
import org.exoplatform.portal.mop.description.DescriptionServiceImpl;
import org.exoplatform.portal.mop.navigation.*;
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
public class TestNavigationMigrationService extends AbstractJCRImplTest {

  private NavigationService          navService;

  private DescriptionService         descriptionService;

  private DescriptionServiceImpl     jcrDescriptionService;

  private NavigationServiceWrapper   jcrNavService;

  private POMSessionManager          manager;

  private NavigationMigrationService migrationService;

  private ModelDataStorage           modelStorage;

  private POMDataStorage             pomStorage;

  public TestNavigationMigrationService(String name) {
    super(name);
  }


  @Override
  protected void setUp() throws Exception {
    MigrationContext.resetForceStop();

    this.pomStorage = getService(POMDataStorage.class);
    this.navService = getService(NavigationService.class);
    this.manager = getService(POMSessionManager.class);
    this.modelStorage = getService(ModelDataStorage.class);
    this.jcrNavService = getService(NavigationServiceWrapper.class);
    this.descriptionService = getService(DescriptionService.class);
    this.jcrDescriptionService = getService(DescriptionServiceImpl.class);
    this.migrationService = getService(NavigationMigrationService.class);

    begin();
  }

  public void testMigrate() throws Exception {
    MOPService mop = manager.getPOMService();
    Site portal = mop.getModel().getWorkspace().addSite(ObjectType.PORTAL_SITE, "testMigrate");
    Navigation defaultNav = portal.getRootNavigation().addChild("default");
    defaultNav.addChild("a");

    restartTransaction(true);

    NavigationContext nav = jcrNavService.loadNavigation(SiteKey.portal("testMigrate"));
    nav.setState(new NavigationState(1));
    jcrNavService.saveNavigation(nav);
    NodeContext root = jcrNavService.loadNode(NodeModel.SELF_MODEL, nav, Scope.ALL, null);
    NodeContext child = root.get("a");
    assertNotNull(nav);
    assertNotNull(child);

    jcrDescriptionService.setDescription(child.getId(), Locale.ENGLISH, new org.exoplatform.portal.mop.State("testDescribe", "testDescribe"));

    sync(true);

    ContainerData container = new ContainerData(null,
                                                "test",
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
    modelStorage.create(new PortalData(null,
                                       "testMigrate",
                                       SiteType.PORTAL.getName(),
                                       null,
                                       null,
                                       null,
                                       new ArrayList<>(),
                                       null,
                                       null,
                                       null,
                                       container,
                                       null));

    sync(true);

    migrationService.doMigrate(new PortalKey(PortalConfig.PORTAL_TYPE, "testMigrate"));
    migrationService.doRemove(new PortalKey(PortalConfig.PORTAL_TYPE, "testMigrate"));

    nav = navService.loadNavigation(SiteKey.portal("testMigrate"));
    assertNotNull(nav);

    root = navService.loadNode(NodeModel.SELF_MODEL, nav, Scope.ALL, null);
    assertNotNull(root);
    child = root.get("a");
    assertNotNull(child);
    assertNotNull(descriptionService.getDescription(child.getId(), Locale.ENGLISH));

    jcrNavService.clearCache();
    nav = jcrNavService.loadNavigation(SiteKey.portal("testMigrate"));
    assertNull(nav);

    // Remove site
    PortalData portalData = new PortalData(null,
                                           "testMigrate",
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
    this.pomStorage.remove(portalData);
    this.pomStorage.save();
    sync(true);
  }
}
