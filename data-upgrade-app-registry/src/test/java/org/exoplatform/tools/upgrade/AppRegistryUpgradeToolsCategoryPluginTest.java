package org.exoplatform.tools.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.application.registry.Application;
import org.exoplatform.application.registry.ApplicationCategory;
import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.application.upgrade.AppRegistryUpgradeToolsCategoryPlugin;
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

public class AppRegistryUpgradeToolsCategoryPluginTest {

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
  public void testAppsMigration() throws WikiException {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("app.name");
    valueParam.setValue("IFramePortlet");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("app.category.name");
    valueParam.setValue("Tools");
    initParams.addParameter(valueParam);

    String toolCategoryName = "Tools";
    ApplicationCategory toolsCategory = createAppCategory(toolCategoryName, "None");
    applicationRegistryService.save(toolsCategory);
    Application IFramePortletApp = createApplication();
    applicationRegistryService.save(toolsCategory,IFramePortletApp );

    try {
      List<Application> apps = applicationRegistryService.getApplications(toolsCategory);
      assertEquals(apps.size(), 1);
      assertEquals(apps.get(0).getApplicationName(), "IFramePortlet");
    } catch (Exception e) {
      fail();
    }

    AppRegistryUpgradeToolsCategoryPlugin appRegistryUpgradeToolsCategoryPlugin  = new AppRegistryUpgradeToolsCategoryPlugin(container,
                                                                                         entityManagerService,
                                                                                         initParams);
      appRegistryUpgradeToolsCategoryPlugin.processUpgrade(null, null);
    try {
      List<Application> apps = applicationRegistryService.getApplications(toolsCategory);
      assertEquals(apps.size(),0);
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

  private Application createApplication() {
    Application app = new Application();
    app.setContentId("web/IFramePortlet");
    app.setApplicationName("IFramePortlet");
    app.setDisplayName("IFramePortlet");
    app.setType(ApplicationType.PORTLET);
    return app;
  }
}
