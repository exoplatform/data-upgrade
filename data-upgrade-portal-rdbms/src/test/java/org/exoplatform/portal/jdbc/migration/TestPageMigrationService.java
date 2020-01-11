package org.exoplatform.portal.jdbc.migration;

import java.util.*;

import javax.persistence.EntityTransaction;

import org.gatein.mop.api.workspace.ObjectType;
import org.gatein.mop.api.workspace.Site;
import org.gatein.mop.core.api.MOPService;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.component.test.*;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.pom.config.POMDataStorage;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.portal.pom.data.*;
import org.exoplatform.portal.pom.data.PageData;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.identity-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.migration-configuration.xml"),
})
public class TestPageMigrationService extends AbstractJCRImplTest {

  private POMDataStorage       pomStorage;

  private ModelDataStorage     modelStorage;

  private PageService          pageService;

  private POMSessionManager    manager;

  private org.exoplatform.portal.mop.page.PageServiceImpl      jcrPageService;

  private PageMigrationService pageMigrationService;

  @Override
  public void setUp() throws Exception {
    MigrationContext.resetForceStop();

    this.pomStorage = getService(POMDataStorage.class);
    this.modelStorage = getService(ModelDataStorage.class);
    this.pageService = getService(PageService.class);
    this.manager = getService(POMSessionManager.class);
    this.jcrPageService = getService(PageServiceImpl.class);
    this.pageMigrationService = getService(PageMigrationService.class);

    begin();
  }

  public void testMigrate() throws Exception {
    MOPService mop = manager.getPOMService();
    Site portal = mop.getModel().getWorkspace().addSite(ObjectType.PORTAL_SITE, "testPageMigrationSite");
    portal.getRootPage().addChild("pages");

    PageKey pageKey = new PageKey(SiteKey.portal("testPageMigrationSite"), "testPageMigration");
    PageState state = new PageState("",
                                    "",
                                    false,
                                    "",
                                    Collections.emptyList(),
                                    "",
                                    Collections.emptyList(),
                                    Collections.emptyList());
    PageContext pageContext = new PageContext(pageKey, state);
    jcrPageService.savePage(pageContext);

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
    pomStorage.save(new PageData(null,
                                 null,
                                 "testPageMigration",
                                 null,
                                 null,
                                 null,
                                 "testPageMigration",
                                 "",
                                 "",
                                 "",
                                 Collections.emptyList(),
                                 Arrays.asList(container),
                                 "portal",
                                 "testPageMigrationSite",
                                 "",
                                 false,
                                 Collections.emptyList(),
                                 Collections.emptyList()));

    restartTransaction(true);

    container = new ContainerData(null,
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
                                       "testPageMigrationSite",
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

    restartTransaction(true);

    pageMigrationService.doMigrate(new PortalKey(PortalConfig.PORTAL_TYPE, "testPageMigrationSite"));
    pageMigrationService.doRemove(new PortalKey(PortalConfig.PORTAL_TYPE, "testPageMigrationSite"));

    restartTransaction(true);

    assertNull(jcrPageService.loadPage(pageKey));
    assertNotNull(pageService.loadPage(pageKey));

    // Clean up portal
    PortalData portalData = new PortalData(null,
                                           "testPageMigrationSite",
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
