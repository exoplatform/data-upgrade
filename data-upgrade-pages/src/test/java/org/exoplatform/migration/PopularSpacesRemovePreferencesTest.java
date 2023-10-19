package org.exoplatform.migration;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.mop.Utils;
import org.exoplatform.portal.mop.page.PageContext;
import org.exoplatform.portal.mop.page.PageService;
import org.exoplatform.portal.mop.page.PageState;
import org.exoplatform.portal.pom.data.ModelDataStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;
@ConfiguredBy({
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml")
})
public class PopularSpacesRemovePreferencesTest extends AbstractKernelTest {


    private static final String    SITE_TYPE           = PortalConfig.PORTAL_TYPE;
    private static final String    SITE_NAME           = "testSite";


    protected PortalContainer container;
    protected PageService pageService;
    protected DataStorage dataStorage;
    protected EntityManagerService entityManagerService;
    protected Page testPage;

    public PopularSpacesRemovePreferencesTest() {
      setForceContainerReload(true);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        container = getContainer();
        pageService = container.getComponentInstanceOfType(PageService.class);
        dataStorage = container.getComponentInstanceOfType(DataStorage.class);
        entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);

        begin();
    }

    @Test
    public void testUpgradePopularSpacesPortlet() throws Exception {
        InitParams initParams = new InitParams();

        ValueParam valueParam = new ValueParam();
        valueParam.setName("product.group.id");
        valueParam.setValue("org.exoplatform.platform");
        initParams.addParameter(valueParam);

        RequestLifeCycle.begin(container);
        PortalConfig portalConfig = dataStorage.getPortalConfig(SITE_TYPE, SITE_NAME);
        if (portalConfig == null) {
            portalConfig = new PortalConfig(SITE_TYPE, SITE_NAME);
            dataStorage.create(portalConfig);
        }
        testPage = createPage("Popular spaces", "gamification-portlets/PopularSpaces");
        PopularSpacesRemovePreferences preferences = new PopularSpacesRemovePreferences(entityManagerService, initParams);
        assertFalse(preferences.isPortletUpdated());
        preferences.processUpgrade(null, null);
        assertTrue(preferences.isPortletUpdated());
    }

    @After
    public void tearDown() throws Exception {
        pageService.destroyPage(testPage.getPageKey());
        end();
    }

    private Page createPage(String pageName, String contentId) throws Exception {
        Page page = new Page(SITE_TYPE, SITE_NAME, pageName);
        page.setAccessPermissions(new String[] { "Everyone" });
        ArrayList<ModelObject> children = new ArrayList<>();
        page.setChildren(children);
        Application<?> app = Application.createPortletApplication();
        children.add(app);
        app.setState(new TransientApplicationState(contentId, "test custom preferences"));
        app.setTheme("theme");
        app.setTitle("title");
        app.setAccessPermissions(new String[] { "Everyone" });
        app.setStorageName("storage");
        PageState pageState = Utils.toPageState(page);
        pageService.savePage(new PageContext(page.getPageKey(), pageState));
        dataStorage.save(page);

        return page;
    }

}
