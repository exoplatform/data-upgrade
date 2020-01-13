package org.exoplatform.portal.jdbc.migration;

import java.util.*;

import org.gatein.mop.api.workspace.ObjectType;
import org.gatein.mop.api.workspace.Site;
import org.gatein.mop.core.api.MOPService;

import org.exoplatform.component.test.*;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.pom.config.POMDataStorage;
import org.exoplatform.portal.pom.config.POMSessionManager;
import org.exoplatform.portal.pom.data.*;
import org.exoplatform.portal.pom.data.PageData;
import org.exoplatform.portal.pom.spi.portlet.Portlet;

@ConfiguredBy({
    @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.identity-configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.migration-configuration.xml"),
})
public class TestAppReferenceMigrationService extends AbstractJCRImplTest {

  private POMDataStorage                                  pomStorage;

  private ModelDataStorage                                modelStorage;

  private PageService                                     pageService;

  private POMSessionManager                               manager;

  private org.exoplatform.portal.mop.page.PageServiceImpl jcrPageService;

  private PageMigrationService                            pageMigrationService;

  private AppReferencesMigrationService                   appReferencesMigrationService;

  @Override
  public void setUp() throws Exception {
    MigrationContext.resetForceStop();

    this.pomStorage = getService(POMDataStorage.class);
    this.modelStorage = getService(ModelDataStorage.class);
    this.pageService = getService(PageService.class);
    this.manager = getService(POMSessionManager.class);
    this.jcrPageService = getService(PageServiceImpl.class);
    this.pageMigrationService = getService(PageMigrationService.class);
    this.appReferencesMigrationService = getService(AppReferencesMigrationService.class);

    begin();
  }

  public void testMigrateWithPageMigration() throws Exception {
    String oldApplicationName = "appToMigrate";
    String oldPortletName = "portletToMigrate";
    String newApplicationName = "appMigrated";
    String newPortletName = "portletMigrated";

    ApplicationReferenceModification applicationReferenceModification = new ApplicationReferenceModification();
    applicationReferenceModification.setOldApplicationName(oldApplicationName);
    applicationReferenceModification.setOldPortletName(oldPortletName);
    applicationReferenceModification.setNewApplicationName(newApplicationName);
    applicationReferenceModification.setNewPortletName(newPortletName);
    appReferencesMigrationService.addApplicationModification(applicationReferenceModification);

    String oldContentId = applicationReferenceModification.getOldContentId();
    String siteName = "testAppMigrationSite";
    String siteType = "portal";
    String pageName = "testAppMigration";

    MOPService mop = manager.getPOMService();
    Site portal = mop.getModel().getWorkspace().addSite(ObjectType.PORTAL_SITE, siteName);
    portal.getRootPage().addChild("pages");
    PortalData portalData = buildSiteLayout(siteName);
    modelStorage.create(portalData);
    restartTransaction(true);

    PageKey pageKey = new PageKey(SiteKey.portal(siteName), pageName);
    PageData pageData = buildPageData(siteName, siteType, pageName, oldContentId);
    PageContext pageContext = getPageContext(pageKey);
    jcrPageService.savePage(pageContext);
    pomStorage.save(pageData);
    restartTransaction(true);

    pageMigrationService.doMigrate(new PortalKey(PortalConfig.PORTAL_TYPE, siteName));
    pageMigrationService.doRemove(new PortalKey(PortalConfig.PORTAL_TYPE, siteName));
    restartTransaction(true);

    assertNull(jcrPageService.loadPage(pageKey));
    PageContext page = pageService.loadPage(pageKey);
    assertNotNull(page);

    PageData savedPage = modelStorage.getPage(new org.exoplatform.portal.pom.data.PageKey(siteType,
                                                                                          siteName,
                                                                                          pageName));
    List<ComponentData> savedChildren = savedPage.getChildren();
    assertNotNull(savedChildren);
    assertEquals(1, savedChildren.size());
    ContainerData savedContainerData = (ContainerData) savedChildren.get(0);
    assertNotNull(savedContainerData);
    assertEquals(1, savedContainerData.getChildren().size());
    @SuppressWarnings("unchecked")
    ApplicationData<Portlet> savedAppData = (ApplicationData<Portlet>) savedContainerData.getChildren().get(0);
    assertNotNull(savedAppData);
    String newContentId = modelStorage.getId(savedAppData.getState());
    assertEquals("appMigrated/portletMigrated", newContentId);

    // Clean up portal
    this.pomStorage.remove(portalData);
    this.pomStorage.save();
    sync(true);
  }

  @SuppressWarnings("unchecked")
  public void testMigrateWithoutPageMigration() throws Exception {
    String oldApplicationName = "appToMigrateInSingle";
    String oldPortletName = "portletToMigrate";
    String newApplicationName = "appMigratedSingle";
    String newPortletName = "portletMigrated";

    ApplicationReferenceModification applicationReferenceModification = new ApplicationReferenceModification();
    applicationReferenceModification.setOldApplicationName(oldApplicationName);
    applicationReferenceModification.setOldPortletName(oldPortletName);
    applicationReferenceModification.setNewApplicationName(newApplicationName);
    applicationReferenceModification.setNewPortletName(newPortletName);

    String oldContentId = applicationReferenceModification.getOldContentId();
    String newContentId = applicationReferenceModification.getNewContentId();
    String siteName = "testAppSingleMigrationSite";
    String siteType = "portal";
    String pageName = "testAppSingleMigration";

    MOPService mop = manager.getPOMService();
    Site portal = mop.getModel().getWorkspace().addSite(ObjectType.PORTAL_SITE, siteName);
    portal.getRootPage().addChild("pages");
    PortalData portalData = buildSiteLayout(siteName);
    modelStorage.create(portalData);
    restartTransaction(true);

    PageKey pageKey = new PageKey(SiteKey.portal(siteName), pageName);
    PageData pageData = buildPageData(siteName, siteType, pageName, oldContentId);
    PageContext pageContext = getPageContext(pageKey);
    jcrPageService.savePage(pageContext);
    pomStorage.save(pageData);
    restartTransaction(true);

    pageMigrationService.doMigrate(new PortalKey(PortalConfig.PORTAL_TYPE, siteName));
    pageMigrationService.doRemove(new PortalKey(PortalConfig.PORTAL_TYPE, siteName));
    restartTransaction(true);

    assertNull(jcrPageService.loadPage(pageKey));
    PageContext page = pageService.loadPage(pageKey);
    assertNotNull(page);

    PageData savedPage = modelStorage.getPage(new org.exoplatform.portal.pom.data.PageKey(siteType,
                                                                                          siteName,
                                                                                          pageName));
    List<ComponentData> savedChildren = savedPage.getChildren();
    assertNotNull(savedChildren);
    assertEquals(1, savedChildren.size());
    ContainerData savedContainerData = (ContainerData) savedChildren.get(0);
    assertNotNull(savedContainerData);
    assertEquals(1, savedContainerData.getChildren().size());
    ApplicationData<Portlet> savedAppData = (ApplicationData<Portlet>) savedContainerData.getChildren().get(0);
    assertNotNull(savedAppData);
    assertEquals(oldContentId, modelStorage.getId(savedAppData.getState()));

    appReferencesMigrationService.addApplicationModification(applicationReferenceModification);
    appReferencesMigrationService.doMigration();
    restartTransaction(true);

    savedPage = modelStorage.getPage(new org.exoplatform.portal.pom.data.PageKey(siteType,
                                                                                 siteName,
                                                                                 pageName));
    savedChildren = savedPage.getChildren();
    assertNotNull(savedChildren);
    assertEquals(1, savedChildren.size());
    savedContainerData = (ContainerData) savedChildren.get(0);
    assertNotNull(savedContainerData);
    assertEquals(1, savedContainerData.getChildren().size());
    savedAppData = (ApplicationData<Portlet>) savedContainerData.getChildren().get(0);
    assertNotNull(savedAppData);
    assertEquals(newContentId, modelStorage.getId(savedAppData.getState()));

    // Clean up portal
    this.pomStorage.remove(portalData);
    this.pomStorage.save();
    sync(true);
  }

  private PageData buildPageData(String siteName, String siteType, String pageName, String oldAppContentId) {
    List<ComponentData> children = buildPage(oldAppContentId);
    return new PageData(null,
                        null,
                        pageName,
                        null,
                        null,
                        null,
                        pageName,
                        "",
                        "",
                        "",
                        Collections.emptyList(),
                        children,
                        siteType,
                        siteName,
                        "",
                        false,
                        Collections.emptyList(),
                        Collections.emptyList());
  }

  private PageContext getPageContext(PageKey pageKey) {
    PageState state = new PageState("",
                                    "",
                                    false,
                                    "",
                                    Collections.emptyList(),
                                    "",
                                    Collections.emptyList(),
                                    Collections.emptyList());
    return new PageContext(pageKey, state);
  }

  private PortalData buildSiteLayout(String siteName) {
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
    return new PortalData(null,
                          siteName,
                          SiteType.PORTAL.getName(),
                          null,
                          null,
                          null,
                          new ArrayList<>(),
                          null,
                          null,
                          null,
                          container,
                          null);
  }

  private List<ComponentData> buildPage(String oldContentId) {
    ApplicationState<Portlet> applicationState = new TransientApplicationState<>(oldContentId, new Portlet());
    ApplicationData<Portlet> applicationData = new ApplicationData<>(null,
                                                                     null,
                                                                     ApplicationType.PORTLET,
                                                                     applicationState,
                                                                     null,
                                                                     "app-title",
                                                                     "app-icon",
                                                                     "app-description",
                                                                     false,
                                                                     true,
                                                                     false,
                                                                     "app-theme",
                                                                     "app-wdith",
                                                                     "app-height",
                                                                     new HashMap<String, String>(),
                                                                     Collections.singletonList("app-edit-permissions"));
    ContainerData containerData = new ContainerData(null,
                                                    "cd-id",
                                                    "cd-name",
                                                    "cd-icon",
                                                    "cd-template",
                                                    "cd-factoryId",
                                                    "cd-title",
                                                    "cd-description",
                                                    "cd-width",
                                                    "cd-height",
                                                    Collections.singletonList("cd-access-permissions"),
                                                    Collections.singletonList("cd-move-apps-permissions"),
                                                    Collections.singletonList("cd-move-containers-permissions"),
                                                    Collections.singletonList((ComponentData) applicationData));
    return Collections.singletonList((ComponentData) containerData);
  }

}
