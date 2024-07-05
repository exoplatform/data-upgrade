package org.exoplatform.migration;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.upgrade.UpgradePluginExecutionContext;
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
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.jdbc.entity.NodeEntity;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.dao.NodeDAO;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationState;
import org.exoplatform.portal.mop.navigation.Node;
import org.exoplatform.portal.mop.navigation.NodeContext;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.service.LayoutService;
import org.exoplatform.portal.mop.service.NavigationService;
import org.exoplatform.portal.pom.data.ContainerData;
import org.exoplatform.portal.pom.data.PortalData;
import org.exoplatform.services.cache.CacheService;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml"),
})
public class NavigationNodesMigrationTest extends AbstractKernelTest {

  protected PortalContainer      container;

  protected NavigationService    navigationService;

  protected LayoutService        layoutService;

  protected EntityManagerService entityManagerService;

  protected CacheService cacheService;

  protected NavigationContext    nav;

  protected String               oldName  = "oldName";

  protected String               newName  = "newName";

  protected String               newLabel = "newLabel";

  @Before
  public void setUp() throws Exception {

    container = PortalContainer.getInstance();
    navigationService = container.getComponentInstanceOfType(NavigationService.class);
    layoutService = container.getComponentInstanceOfType(LayoutService.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    cacheService = container.getComponentInstanceOfType(CacheService.class);

    begin();
    injectData();
  }

  @After
  public void tearDown() {
    purgeData();
    end();
  }

  @Test
  public void testNodeMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("old.nav.name");
    valueParam.setValue(oldName);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.nav.name");
    valueParam.setValue(newName);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.nav.label");
    valueParam.setValue(newLabel);
    initParams.addParameter(valueParam);

    NodeContext<?> node = navigationService.loadNode(Node.MODEL, nav, Scope.ALL, null);
    NodeContext<?> page = node.get(oldName);
    assertNotNull(page);
    assertEquals(oldName, page.getName());

    NavigationNotesMigration noteMigration = new NavigationNotesMigration(container, entityManagerService, cacheService, initParams);
    assertEquals(0, noteMigration.getNodesUpdatedCount());
    noteMigration.processUpgrade(null, null);
    assertEquals(1, noteMigration.getNodesUpdatedCount());

    assertTrue(noteMigration.shouldProceedToUpgrade("v1", "v1", new UpgradePluginExecutionContext("v1;0")));

    end();
    begin();

    NodeDAO nodeDAO = CommonsUtils.getService(NodeDAO.class);
    NodeEntity newPage = nodeDAO.find(Long.valueOf(page.getId()));
    assertNotNull(newPage);
    assertEquals(newName, newPage.getName());
    assertEquals(newLabel, newPage.getLabel());

  }

  protected void injectData() throws Exception {

    this.createSite(SiteType.PORTAL, "new_node");

    nav = navigationService.loadNavigation(SiteKey.portal("new_node"));
    NodeContext<?> node = navigationService.loadNode(Node.MODEL, nav, Scope.ALL, null);
    node.add(0, oldName);
    navigationService.saveNode(node, null);
  }

  protected void purgeData() {
    navigationService.destroyNavigation(nav);
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  protected void createSite(SiteType type, String siteName) throws Exception {
    ContainerData container = new ContainerData(null,
                                                "testcontainer_" + siteName,
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                "",
                                                null,
                                                null,
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                Collections.emptyList());
    PortalData portal = new PortalData(null,
                                       siteName,
                                       type.getName(),
                                       null,
                                       null,
                                       null,
                                       new ArrayList<>(),
                                       null,
                                       null,
                                       null,
                                       container,
                                       true,
                                       5,
                                       0);
    this.layoutService.create(new PortalConfig(portal));

    NavigationContext nav = new NavigationContext(type.key(siteName), new NavigationState(1));
    navigationService.saveNavigation(nav);
  }

}
