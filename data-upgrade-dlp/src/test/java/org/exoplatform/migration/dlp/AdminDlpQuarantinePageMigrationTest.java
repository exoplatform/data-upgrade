package org.exoplatform.migration.dlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.Application;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.config.model.TransientApplicationState;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.Utils;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NavigationState;
import org.exoplatform.portal.mop.navigation.NodeContext;
import org.exoplatform.portal.mop.navigation.NodeModel;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.page.PageContext;
import org.exoplatform.portal.mop.page.PageService;
import org.exoplatform.portal.mop.page.PageState;
import org.exoplatform.portal.pom.data.ModelDataStorage;

public class AdminDlpQuarantinePageMigrationTest {

  private static final String    SITE_TYPE      = PortalConfig.GROUP_TYPE;

  private static final String    SITE_NAME      = "/platform/administrators";

  private static final String    DLP_QUARANTINE = "dlp-quarantine";

  protected PortalContainer      container;

  protected PageService          pageService;

  protected NavigationService    navigationService;

  protected ModelDataStorage     modelDataStorage;

  protected DataStorage          dataStorage;

  protected Page                 dlpQuarantinePage;

  @Before
  public void setUp() throws Exception {
    container = PortalContainer.getInstance();

    pageService = container.getComponentInstanceOfType(PageService.class);
    navigationService = container.getComponentInstanceOfType(NavigationService.class);
    modelDataStorage = container.getComponentInstanceOfType(ModelDataStorage.class);
    dataStorage = container.getComponentInstanceOfType(DataStorage.class);

    begin();
    injectData();
  }

  @After
  public void tearDown() {
    purgeData();
    end();
  }

  @Test
  public void testAdminDlpQuarantinePageMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    NavigationContext navigationContext = navigationService.loadNavigation(SiteKey.group(SITE_NAME));
    NodeContext node = navigationService.loadNode(NodeModel.SELF_MODEL, navigationContext, Scope.ALL, null);
    Page page = new Page(SITE_TYPE, SITE_NAME, DLP_QUARANTINE);
    assertNotNull(navigationContext);
    assertNotNull(node.get(DLP_QUARANTINE));
    assertNotNull(pageService.loadPage(page.getPageKey()));
    
    AdminDlpQuarantinePageMigration adminDlpQuarantinePageMigration = new AdminDlpQuarantinePageMigration(initParams,
                                                                                                          navigationService,
                                                                                                          pageService);
    adminDlpQuarantinePageMigration.processUpgrade(null, null);
    node = navigationService.loadNode(NodeModel.SELF_MODEL, navigationContext, Scope.ALL, null);
    assertEquals(node.get(DLP_QUARANTINE), null);
    assertEquals(pageService.loadPage(page.getPageKey()), null);
  }

  protected void injectData() throws Exception {
    PortalConfig portalConfig = dataStorage.getPortalConfig(SITE_TYPE, SITE_NAME);
    if (portalConfig == null) {
      portalConfig = new PortalConfig(SITE_TYPE, SITE_NAME);
      dataStorage.create(portalConfig);
    }
    dlpQuarantinePage = createPage(DLP_QUARANTINE, "contentId");
    navigationService.saveNavigation(new NavigationContext(new SiteKey(SiteType.GROUP, SITE_NAME), new NavigationState(1)));
    NavigationContext navigationContext = navigationService.loadNavigation(SiteKey.group(SITE_NAME));
    NodeContext node = navigationService.loadNode(NodeModel.SELF_MODEL, navigationContext, Scope.ALL, null);
    NavigationState navigationState = new NavigationState(navigationContext.getState().getPriority() + 1);
    NodeContext dlpNodeContext = node.add(node.getIndex(), DLP_QUARANTINE);
    dlpNodeContext.setState(dlpNodeContext.getState().builder().pageRef(dlpQuarantinePage.getPageKey()).build());
    navigationService.saveNavigation(navigationContext);
    navigationService.saveNode(dlpNodeContext, null);
  }

  protected void purgeData() {
    pageService.destroyPage(dlpQuarantinePage.getPageKey());
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private Page createPage(String pageName, String contentId) throws Exception {
    Page page = new Page(SITE_TYPE, SITE_NAME, pageName);
    page.setAccessPermissions(new String[] { "Everyone" });
    ArrayList<ModelObject> children = new ArrayList<>();
    page.setChildren(children);
    Application<?> app = Application.createPortletApplication();
    children.add(app);
    app.setState(new TransientApplicationState(contentId, null));
    app.setTheme("theme");
    app.setTitle("title");
    app.setAccessPermissions(new String[] { "Everyone" });

    PageState pageState = Utils.toPageState(page);
    pageService.savePage(new PageContext(page.getPageKey(), pageState));
    dataStorage.save(page);

    return page;
  }
}
