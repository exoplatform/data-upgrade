package org.exoplatform.category.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.exoplatform.application.upgrade.AppRegistryCategoryUpgradePlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.application.registry.Application;
import org.exoplatform.application.registry.ApplicationCategory;
import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.model.ApplicationType;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.service.DataStorage;

public class AppRegistryCategoryUpgradePluginTest {

  protected PortalContainer            container;

  protected ApplicationRegistryService applicationRegistryService;

  protected DataStorage                dataStorage;

  protected EntityManagerService       entityManagerService;

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    applicationRegistryService = CommonsUtils.getService(ApplicationRegistryService.class);
    dataStorage = CommonsUtils.getService(DataStorage.class);
    entityManagerService = CommonsUtils.getService(EntityManagerService.class);
    begin();
  }

  @After
  public void tearDown() {
    end();
  }

  @Test
  public void testCategoryClean() throws WikiException {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    String toolCategoryName = "Tools";
    String analyticsCategoryName = "Analytics";
    ApplicationCategory toolsCategory = createAppCategory(toolCategoryName, "None");
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
      assertEquals(cats.size(), 2);
      assertEquals(apps.size(), 2);
      assertEquals(apps.get(0).getApplicationName(), "IFramePortlet");
      assertEquals(apps.get(1).getApplicationName(), "AnalyticsPortlet");
    } catch (Exception e) {
      fail();
    }

    AppRegistryCategoryUpgradePlugin appRegistryCategoryUpgradePlugin = new AppRegistryCategoryUpgradePlugin(container,
                                                                                                             entityManagerService,
                                                                                                             initParams);
    appRegistryCategoryUpgradePlugin.processUpgrade(null, null);
    try {
      List<Application> apps = applicationRegistryService.getAllApplications();
      assertEquals(apps.size(), 0);
      List<ApplicationCategory> cats = applicationRegistryService.getApplicationCategories();
      assertEquals(cats.size(), 0);
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
