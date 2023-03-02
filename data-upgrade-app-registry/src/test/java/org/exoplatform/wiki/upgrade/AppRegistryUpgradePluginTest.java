package org.exoplatform.wiki.upgrade;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.application.registry.Application;
import org.exoplatform.application.registry.ApplicationCategory;
import org.exoplatform.application.registry.ApplicationRegistryService;
import org.exoplatform.application.upgrade.AppRegistryUpgradePlugin;
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
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.service.DataStorage;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration-local.xml"),
})
public class AppRegistryUpgradePluginTest extends AbstractKernelTest {

  protected PortalContainer         container;

  protected ApplicationRegistryService             applicationRegistryService;

  protected DataStorage             dataStorage;

  protected EntityManagerService    entityManagerService;


  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    applicationRegistryService= CommonsUtils.getService(ApplicationRegistryService.class);
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
    valueParam.setName("old.content.id");
    valueParam.setValue("oldContentId");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.description");
    valueParam.setValue("newDescription");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.display.name");
    valueParam.setValue("newDisplayName");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.app.name");
    valueParam.setValue("newAppName");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.content.id");
    valueParam.setValue("newContentId");
    initParams.addParameter(valueParam);

    String officeCategoryName = "Office";
    ApplicationCategory officeCategory = createAppCategory(officeCategoryName, "None");
    applicationRegistryService.save(officeCategory);
    Application msApp = createApplication();
    applicationRegistryService.save(officeCategory, msApp);

    try {
      List<Application> apps = applicationRegistryService.getApplications(officeCategory);
      assertEquals(apps.size(),1);
      assertEquals(apps.get(0).getContentId(),"oldContentId");
    } catch (Exception e) {
      fail();
    }

    AppRegistryUpgradePlugin wikiAppRegistryUpgradePlugin = new AppRegistryUpgradePlugin(container, entityManagerService, initParams);
    wikiAppRegistryUpgradePlugin.processUpgrade(null, null);
    try {
      List<Application> apps = applicationRegistryService.getApplications(officeCategory);
      assertEquals(apps.get(0).getContentId(),"newContentId");
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
    app.setContentId("oldContentId");
    app.setApplicationName("appName");
    app.setDisplayName("appName");
    app.setType(ApplicationType.PORTLET);
    return app;
  }

}
