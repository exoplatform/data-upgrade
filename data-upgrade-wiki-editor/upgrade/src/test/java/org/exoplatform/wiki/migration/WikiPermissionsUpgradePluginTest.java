package org.exoplatform.wiki.migration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.wiki.jpa.JPADataStorage;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.Permission;
import org.exoplatform.wiki.mow.api.PermissionEntry;
import org.exoplatform.wiki.mow.api.PermissionType;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.service.IDType;
import org.exoplatform.wiki.service.WikiService;

@RunWith(PowerMockRunner.class)
public class WikiPermissionsUpgradePluginTest {

  @Mock
  WikiService    wikiService;

  @Mock
  JPADataStorage jpaDataStorage;

  @Test
  public void testWikiPermissionsMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.addons.platform");
    initParams.addParameter(valueParam);
    
    List<Wiki> allWikiList = new ArrayList<Wiki>();
    
    // Given
    Wiki wiki1 = new Wiki();
    wiki1.setType("portal");
    wiki1.setId("wiki1");
    wiki1.setOwner("wiki1");
    Permission[] allPermissions = new Permission[] {
        new Permission(PermissionType.ADMINPAGE, true),
        new Permission(PermissionType.ADMINSPACE, true)
    };
    List<PermissionEntry> permissions = new ArrayList<>();
    PermissionEntry permissionEntry1 = new PermissionEntry("*:/group1", "", IDType.MEMBERSHIP, allPermissions);
    PermissionEntry permissionEntry2 = new PermissionEntry("/group2", "", IDType.GROUP, allPermissions);
    permissions.add(permissionEntry1);
    permissions.add(permissionEntry2);
    wiki1.setPermissions(permissions);
    
    Page wiki1HomePage = new Page();
    wiki1HomePage.setWikiId(wiki1.getId());
    wiki1HomePage.setWikiType(wiki1.getType());
    wiki1HomePage.setWikiOwner(wiki1.getOwner());
    wiki1HomePage.setName("page0");
    wiki1HomePage.setTitle("Page 0");
    PermissionEntry permissionEntry3= new PermissionEntry("/group3", "", IDType.GROUP, allPermissions);
    permissions.add(permissionEntry3);
    wiki1.setPermissions(permissions);
    wiki1.setWikiHome(wiki1HomePage);
    allWikiList.add(wiki1);
    
    List<Page> allPagesList = new ArrayList<Page>();
    allPagesList.add(wiki1HomePage);
    
    List<PermissionEntry> defaultPermissions = new ArrayList<>();
    PermissionEntry groupPermissionEntry3 = new PermissionEntry("*:/group4", "", IDType.MEMBERSHIP, allPermissions);
    defaultPermissions.add(groupPermissionEntry3);
    
    when(wikiService.getAllWikis()).thenReturn(allWikiList);
    when(wikiService.getWikiDefaultPermissions(any(), any())).thenReturn(defaultPermissions);
    when(wikiService.getPagesOfWiki(any(), any())).thenReturn(allPagesList);
    when(wikiService.getPagesOfWiki(any(), any())).thenReturn(allPagesList);
    when(jpaDataStorage.getWikiHomePageDefaultPermissions(any(), any())).thenReturn(defaultPermissions);
    
    WikiPermissionsUpgradePlugin wikiPermissionsUpgradePlugin = new WikiPermissionsUpgradePlugin(initParams,
                                                                                                 wikiService,
                                                                                                 jpaDataStorage);
    wikiPermissionsUpgradePlugin.processUpgrade(null, null);
    assertEquals(1, wikiPermissionsUpgradePlugin.getWikiPermissionsUpdatedCount());
    assertEquals(1, wikiPermissionsUpgradePlugin.getWikiPagesPermissionsUpdatedCount());
    assertEquals(1, wiki1.getPermissions().size());
    assertEquals("*:/group4", wiki1.getPermissions().get(0).getId());
    assertEquals(1, wiki1HomePage.getPermissions().size());
  }
}
