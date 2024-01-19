package org.exoplatform.category.upgrade;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.application.registry.Application;
import org.exoplatform.application.registry.ApplicationCategory;
import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.application.upgrade.CleanAppRegistryCategoryUpgradePlugin;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.model.ApplicationType;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/application-registry-initializer-configuration.xml"),
})
public class CleanAppRegistryCategoryUpgradePluginTest extends AbstractKernelTest {

  protected PortalContainer            container;

  protected ApplicationRegistryService applicationRegistryService;

  protected EntityManagerService       entityManagerService;

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    applicationRegistryService = CommonsUtils.getService(ApplicationRegistryService.class);
    entityManagerService = CommonsUtils.getService(EntityManagerService.class);
    begin();
  }

  @After
  public void tearDown() {
    end();
  }

  @Test
  @ExoTransactional
  public void testAppRegistryCategoryUpgradePlugin() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    String toolsCategoryName = "Tools";
    String analyticsCategoryName = "Analytics";
    ApplicationCategory toolsCategory = createAppCategory(toolsCategoryName, "None");
    ApplicationCategory analytics = createAppCategory(analyticsCategoryName, "None");
    applicationRegistryService.save(toolsCategory);
    applicationRegistryService.save(analytics);
    Application IFramePortletApp = createApplication("IFramePortlet", "web/IFramePortlet");
    Application AnalyticsPortletApp = createApplication("AnalyticsPortlet", "analytics/AnalyticsPortlet");
    applicationRegistryService.save(toolsCategory, IFramePortletApp);
    applicationRegistryService.save(analytics, AnalyticsPortletApp);

    try {
      List<Application> apps = applicationRegistryService.getAllApplications();
      List<ApplicationCategory> cats = applicationRegistryService.getApplicationCategories();
      assertFalse(apps.isEmpty());
      assertFalse(cats.isEmpty());
      // assert that the tools category is created with the iframe portlet as the application
      ApplicationCategory tools = applicationRegistryService.getApplicationCategory(toolsCategoryName);
      assertNotNull(tools);
      assertTrue(tools.getApplications().size() == 2);
      assertTrue(tools.getApplications().stream().anyMatch(application -> application.getApplicationName().equals("IFramePortlet")));
      //assert that the analytics category is created with the AnalyticsPortlet as the application
      ApplicationCategory analyticsCategory = applicationRegistryService.getApplicationCategory(analyticsCategoryName);
      assertNotNull(analyticsCategory);
      assertTrue(analyticsCategory.getApplications().size() == 1);
      assertTrue(analyticsCategory.getApplications().stream().anyMatch(application -> application.getApplicationName().equals("AnalyticsPortlet")));
    } catch (Exception e) {
      fail();
    }

    CleanAppRegistryCategoryUpgradePlugin appRegistryCategoryUpgradePlugin =
                                                                           new CleanAppRegistryCategoryUpgradePlugin(container,
                                                                                                                     entityManagerService,
                                                                                                                     initParams);
    appRegistryCategoryUpgradePlugin.processUpgrade(null, null);
    try {
      List<Application> apps = applicationRegistryService.getAllApplications();
      assertTrue(!apps.isEmpty());
      List<ApplicationCategory> cats = applicationRegistryService.getApplicationCategories();
      assertTrue(!cats.isEmpty());
      // assert that the tools category is recreated with the WhoIsOnLinePortlet as the application
      ApplicationCategory tools = applicationRegistryService.getApplicationCategory(toolsCategoryName);
      assertNotNull(tools);
      assertTrue(tools.getApplications().size() == 1);
      assertTrue(tools.getApplications().get(0).getApplicationName().equals("WhoIsOnLinePortlet"));
      // assert that the analytics category is removed
      ApplicationCategory analyticsCategory = applicationRegistryService.getApplicationCategory(analyticsCategoryName);
      assertNull(analyticsCategory);
    } catch (Exception e) {
      fail();
    }
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  private ApplicationCategory createAppCategory(String categoryName, String categoryDes) {
    ApplicationCategory category = new ApplicationCategory();
    category.setName(categoryName);
    category.setDisplayName(categoryName);
    category.setDescription(categoryDes);
    return category;
  }

  private Application createApplication(String appName, String contentId) {
    Application app = new Application();
    app.setContentId(contentId);
    app.setApplicationName(appName);
    app.setDisplayName(appName);
    app.setType(ApplicationType.PORTLET);
    return app;
  }
}
