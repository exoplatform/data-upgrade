package org.exoplatform.migration;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.model.Application;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.config.model.TransientApplicationState;
import org.exoplatform.portal.mop.Utils;
import org.exoplatform.portal.mop.page.PageContext;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.mop.page.PageState;
import org.exoplatform.portal.mop.service.LayoutService;
import org.exoplatform.services.cache.CacheService;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml"),
})
public class PagesMigrationTest extends AbstractKernelTest {

  private static final String    SITE_TYPE           = PortalConfig.PORTAL_TYPE;

  private static final String    SITE_NAME           = "testSite";

  private static final String    PAGE_TO_KEEP_NAME   = "page2";

  private static final String    PAGE_TO_CHANGE_NAME = "page1";

  protected PortalContainer      container;

  protected LayoutService        layoutService;

  protected EntityManagerService entityManagerService;

  protected CacheService         cacheService;

  protected Page                 changeThisPage;

  protected Page                 keepThisPage;

  protected String               oldContent          = "app/oldApp";

  protected String               newContent          = "newApp/newApp";

  @Before
  public void setUp() throws Exception {
    super.setUp();
    container = getContainer();

    layoutService = container.getComponentInstanceOfType(LayoutService.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    cacheService = container.getComponentInstanceOfType(CacheService.class);

    begin();
    injectData();
    restartTransaction();
  }

  @After
  public void tearDown() {
    purgeData();
    end();
  }

  @Test
  public void testPageMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("old.application.contentId");
    valueParam.setValue(oldContent);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.application.contentId");
    valueParam.setValue(newContent);
    initParams.addParameter(valueParam);

    Page page = layoutService.getPage(new PageKey(SITE_TYPE,
                                                  SITE_NAME,
                                                  PAGE_TO_CHANGE_NAME));
    assertNotNull(page);
    assertEquals(1, page.getChildren().size());
    page.getChildren().get(0);
    Application<?> componentData = (Application<?>) page.getChildren().get(0);
    String contentId = layoutService.getId(componentData.getState());
    assertEquals(oldContent, contentId);

    page = layoutService.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_KEEP_NAME));
    assertNotNull(page);
    assertEquals(1, page.getChildren().size());
    componentData = (Application<?>) page.getChildren().get(0);
    contentId = layoutService.getId(componentData.getState());
    assertEquals(newContent, contentId);

    PagesMigration pagesMigration = new PagesMigration(container, entityManagerService, cacheService, initParams);
    assertEquals(0, pagesMigration.getPagesUpdatedCount());
    pagesMigration.processUpgrade(null, null);
    assertEquals(1, pagesMigration.getPagesUpdatedCount());

    assertTrue(pagesMigration.shouldProceedToUpgrade("v1", "v1", new UpgradePluginExecutionContext("v1;0")));

    restartTransaction();

    page = layoutService.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_CHANGE_NAME));
    assertNotNull(page);
    assertEquals(1, page.getChildren().size());
    componentData = (Application<?>) page.getChildren().get(0);
    contentId = layoutService.getId(componentData.getState());
    assertEquals(newContent, contentId);

    page = layoutService.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_KEEP_NAME));
    assertNotNull(page);
    assertEquals(1, page.getChildren().size());
    componentData = (Application<?>) page.getChildren().get(0);
    contentId = layoutService.getId(componentData.getState());
    assertEquals(newContent, contentId);
  }

  protected void injectData() throws Exception {
    PortalConfig portalConfig = layoutService.getPortalConfig(SITE_TYPE, SITE_NAME);
    if (portalConfig == null) {
      portalConfig = new PortalConfig(SITE_TYPE, SITE_NAME);
      layoutService.create(portalConfig);
    }

    changeThisPage = createPage(PAGE_TO_CHANGE_NAME, oldContent);
    keepThisPage = createPage(PAGE_TO_KEEP_NAME, newContent);
  }

  protected void purgeData() {
    layoutService.remove(changeThisPage.getPageKey());
    layoutService.remove(keepThisPage.getPageKey());
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
    layoutService.save(new PageContext(page.getPageKey(), pageState), page);
    return page;
  }
}
