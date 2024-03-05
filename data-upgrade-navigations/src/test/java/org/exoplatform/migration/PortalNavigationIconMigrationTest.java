package org.exoplatform.migration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.jdbc.entity.NodeEntity;
import org.exoplatform.portal.jdbc.entity.PageEntity;
import org.exoplatform.portal.jdbc.entity.SiteEntity;
import org.exoplatform.portal.mop.NodeTarget;
import org.exoplatform.portal.mop.PageType;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.dao.NodeDAO;
import org.exoplatform.portal.mop.dao.PageDAO;
import org.exoplatform.portal.mop.dao.SiteDAO;

@ConfiguredBy({ @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml") })
public class PortalNavigationIconMigrationTest extends AbstractKernelTest {
  InitParams                            initParams = new InitParams();
  private PortalContainer               container;
  private EntityManagerService          entityManagerService;
  private PortalNavigationIconMigration portalNavigationIconMigration;
  private SiteDAO                       siteDAO;
  private PageDAO                       pageDAO;
  private NodeDAO                       nodeDAO;

  @Before
  public void setUp() {
    container = getContainer();
    siteDAO = container.getComponentInstanceOfType(SiteDAO.class);
    pageDAO = container.getComponentInstanceOfType(PageDAO.class);
    nodeDAO = container.getComponentInstanceOfType(NodeDAO.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    begin();
    ValueParam productGroupIdValueParam = new ValueParam();
    productGroupIdValueParam.setName("product.group.id");
    productGroupIdValueParam.setValue("org.exoplatform.platform");
    ValueParam portalNodeNamesValueParam = new ValueParam();
    portalNodeNamesValueParam.setName("portal.node.names");
    portalNodeNamesValueParam.setValue("external-stream");
    ValueParam portalNodeIconsValueParam = new ValueParam();
    portalNodeIconsValueParam.setName("portal.node.icons");
    portalNodeIconsValueParam.setValue("fas fa-user-lock");
    initParams.addParameter(productGroupIdValueParam);
    initParams.addParameter(portalNodeNamesValueParam);
    initParams.addParameter(portalNodeIconsValueParam);
    this.portalNavigationIconMigration = new PortalNavigationIconMigration(entityManagerService, initParams);
  }

  @After
  public void tearDown() throws Exception {
    end();
  }

  @Test
  public void testPortalNavigationIconMigration() {

    SiteEntity siteEntity = new SiteEntity();
    siteEntity.setName("dw");
    siteEntity.setSiteType(SiteType.PORTAL);
    siteDAO.create(siteEntity);
    siteEntity = siteDAO.findByType(SiteType.PORTAL).stream().filter(e -> e.getName().equals("dw")).toList().get(0);
    //
    assertNotNull(siteEntity);
    PageEntity pageEntity = new PageEntity();
    pageEntity.setName("stream");
    pageEntity.setOwner(siteEntity);
    pageEntity.setPageType(PageType.PAGE);
    pageDAO.create(pageEntity);
    pageEntity = pageDAO.findAll().stream().filter(e -> e.getName().equals("stream")).toList().get(0);
    //
    assertNotNull(pageEntity);
    NodeEntity nodeEntity = new NodeEntity();
    nodeEntity.setName("external-stream");
    nodeEntity.setIcon(null);
    nodeEntity.setPage(pageEntity);
    nodeEntity.setTarget(NodeTarget.NEW_TAB);
    nodeDAO.create(nodeEntity);
    nodeEntity = nodeDAO.findAllByPage(pageEntity.getId()).stream().filter(e -> e.getName().equals("external-stream")).toList().get(0);
    //
    assertNotNull(nodeEntity);
    restartTransaction();
    portalNavigationIconMigration.processUpgrade(null, null);
    //
    assertEquals(1, portalNavigationIconMigration.getMigratedPortalNodeIconsNodeIcons());
    nodeEntity = nodeDAO.find(nodeEntity.getId());
    assertNotNull(nodeEntity);
    assertNotNull(nodeEntity.getIcon());
    assertEquals("fas fa-user-lock", nodeEntity.getIcon());
  }

}
