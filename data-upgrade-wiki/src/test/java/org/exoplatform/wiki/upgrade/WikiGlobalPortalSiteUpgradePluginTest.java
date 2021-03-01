package org.exoplatform.wiki.upgrade;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.commons.codec.binary.StringUtils;
import org.junit.*;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.mow.api.*;
import org.exoplatform.wiki.service.WikiService;

public class WikiGlobalPortalSiteUpgradePluginTest {

  protected PortalContainer         container;

  protected WikiService             wikiService;

  protected EntityManagerService    entityManagerService;

  protected UserPortalConfigService portalConfigService;

  protected DataStorage             dataStorage;

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();

    wikiService = container.getComponentInstanceOfType(WikiService.class);
    portalConfigService = container.getComponentInstanceOfType(UserPortalConfigService.class);
    dataStorage = container.getComponentInstanceOfType(DataStorage.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);

    begin();
  }

  @After
  public void tearDown() {
    end();
  }

  @Test
  public void testWikiMigration() throws WikiException {
    InitParams initParams = new InitParams();

    String globalPortal = portalConfigService.getGlobalPortal();
    String defaultPortal = portalConfigService.getDefaultPortal();

    wikiService.createWiki(PortalConfig.PORTAL_TYPE, globalPortal);

    List<Page> pages = wikiService.getPagesOfWiki(PortalConfig.PORTAL_TYPE, globalPortal);
    int initialWikiPages = pages.size();
    assertTrue(initialWikiPages > 0);

    Wiki defaultSiteWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, defaultPortal);
    assertNull(defaultSiteWiki);

    WikiGlobalPortalSiteUpgradePlugin wikiGlobalPortalSiteUpgradePlugin =
                                                                        new WikiGlobalPortalSiteUpgradePlugin(container,
                                                                                                              wikiService,
                                                                                                              portalConfigService,
                                                                                                              dataStorage,
                                                                                                              entityManagerService,
                                                                                                              initParams);
    wikiGlobalPortalSiteUpgradePlugin.processUpgrade(null, null);

    defaultSiteWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, defaultPortal);
    assertNotNull(defaultSiteWiki);

    Wiki globalSiteWiki = wikiService.getWikiByTypeAndOwner(PortalConfig.PORTAL_TYPE, globalPortal);
    assertNull(globalSiteWiki);

    List<PermissionEntry> defaultSiteWikiPermissions = defaultSiteWiki.getPermissions();
    assertNotNull(defaultSiteWikiPermissions);
    assertTrue(defaultSiteWikiPermissions.stream()
                                         .noneMatch(permissionEntry -> StringUtils.equals(permissionEntry.getId(),
                                                                                          IdentityConstants.ANY)));

    pages = wikiService.getPagesOfWiki(PortalConfig.PORTAL_TYPE, defaultPortal);
    assertNotNull(pages);
    assertEquals(initialWikiPages, pages.size());
    for (Page page : pages) {
      List<PermissionEntry> pagePermissions = page.getPermissions();
      assertNotNull(pagePermissions);
      assertTrue(pagePermissions.stream()
                                .noneMatch(permissionEntry -> StringUtils.equals(permissionEntry.getId(),
                                                                                 IdentityConstants.ANY)));
    }
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

}
