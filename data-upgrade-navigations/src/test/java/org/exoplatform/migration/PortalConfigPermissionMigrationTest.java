package org.exoplatform.migration;

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
import org.exoplatform.portal.jdbc.entity.PermissionEntity;
import org.exoplatform.portal.jdbc.entity.SiteEntity;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.dao.NodeDAO;
import org.exoplatform.portal.mop.dao.PermissionDAO;
import org.exoplatform.portal.mop.dao.SiteDAO;
import org.exoplatform.portal.mop.navigation.*;
import org.exoplatform.portal.mop.service.LayoutService;
import org.exoplatform.portal.mop.service.NavigationService;
import org.exoplatform.portal.pom.data.ContainerData;
import org.exoplatform.portal.pom.data.PortalData;
import org.exoplatform.services.cache.CacheService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfiguredBy({
        @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
        @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
        @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
        @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml"),
})
public class PortalConfigPermissionMigrationTest extends AbstractKernelTest {

    protected PortalContainer container;

    protected NavigationService navigationService;

    protected LayoutService layoutService;
    protected CacheService cacheService;

    protected EntityManagerService entityManagerService;

    protected NavigationContext nav;

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
    protected void injectData() throws Exception {

        this.createSite(SiteType.GROUP, "/my_group_name");

        nav = navigationService.loadNavigation(SiteKey.group("/my_group_name"));

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

        List<String> accessPermission = new ArrayList<>();
        accessPermission.add("@owner@");

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
                "",
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
                accessPermission,
                "manager:@owner@",
                null,
                null,
                container,
                true,
                8,
                0);
        this.layoutService.create(new PortalConfig(portal));

        NavigationContext navigation = new NavigationContext(type.key(siteName), new NavigationState(1));
        navigationService.saveNavigation(navigation);
    }



    @Test
    public void testSiteConfigMigration() {
        InitParams initParams = new InitParams();
        PortalConfigPermissionMigration portalConfigPermissionMigration = new PortalConfigPermissionMigration(container, entityManagerService,cacheService,initParams);
        portalConfigPermissionMigration.processUpgrade(null, null);
        end();
        begin();

        SiteDAO siteDAO = CommonsUtils.getService(SiteDAO.class);
        SiteEntity site = siteDAO.findByKey(new SiteKey(SiteType.GROUP,"/my_group_name"));
        assertNotNull(site);

        PermissionDAO permissionDAO = CommonsUtils.getService(PermissionDAO.class);
        List<PermissionEntity> accessPermissionEntityList = permissionDAO.getPermissions(SiteEntity.class.getName(),site.getId(), PermissionEntity.TYPE.ACCESS);
        List<PermissionEntity> editPermission = permissionDAO.getPermissions(SiteEntity.class.getName(),site.getId(), PermissionEntity.TYPE.EDIT);

        assertEquals(1, accessPermissionEntityList.size());
        assertEquals("/my_group_name", accessPermissionEntityList.get(0).getPermission());
        assertEquals(1, editPermission.size());
        assertEquals("manager:/my_group_name", editPermission.get(0).getPermission());
    }
}
