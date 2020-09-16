package org.exoplatform.agenda.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.junit.*;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.mop.Utils;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.portal.pom.data.ApplicationData;
import org.exoplatform.portal.pom.data.ModelDataStorage;
import org.exoplatform.portal.pom.data.PageData;
import org.exoplatform.portal.pom.data.PageKey;

public class PagesMigrationTest {

  private static final String    SITE_TYPE           = PortalConfig.PORTAL_TYPE;

  private static final String    SITE_NAME           = "testSite";

  private static final String    PAGE_TO_KEEP_NAME   = "page2";

  private static final String    PAGE_TO_CHANGE_NAME = "page1";

  protected PortalContainer      container;

  protected PageService          pageService;

  protected ModelDataStorage     modelDataStorage;

  protected DataStorage          dataStorage;

  protected EntityManagerService entityManagerService;

  protected Page                 changeThisPage;

  protected Page                 keepThisPage;

  protected String               oldContent          = "app/oldApp";

  protected String               newContent          = "newApp/newApp";

  @Before
  public void setUp() throws Exception {
    container = PortalContainer.getInstance();

    pageService = container.getComponentInstanceOfType(PageService.class);
    modelDataStorage = container.getComponentInstanceOfType(ModelDataStorage.class);
    dataStorage = container.getComponentInstanceOfType(DataStorage.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);

    begin();
    injectData();
  }

  @After
  public void tearDown() {
    purgeData();
    end();
  }

  @Test
  public void testPageMigration() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("old.application.contentId");
    valueParam.setValue(oldContent);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.application.contentId");
    valueParam.setValue(newContent);
    initParams.addParameter(valueParam);

    PageData pageData = modelDataStorage.getPage(new PageKey(SITE_TYPE,
                                                             SITE_NAME,
                                                             PAGE_TO_CHANGE_NAME));
    assertNotNull(pageData);
    assertEquals(1, pageData.getChildren().size());
    ApplicationData<?> componentData = (ApplicationData<?>) pageData.getChildren().get(0);
    String contentId = dataStorage.getId(componentData.getState());
    assertEquals(oldContent, contentId);

    pageData = modelDataStorage.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_KEEP_NAME));
    assertNotNull(pageData);
    assertEquals(1, pageData.getChildren().size());
    componentData = (ApplicationData<?>) pageData.getChildren().get(0);
    contentId = dataStorage.getId(componentData.getState());
    assertEquals(newContent, contentId);

    PagesMigration pagesMigration = new PagesMigration(container, entityManagerService, initParams);
    assertEquals(0, pagesMigration.getPagesUpdatedCount());
    pagesMigration.processUpgrade(null, null);
    assertEquals(1, pagesMigration.getPagesUpdatedCount());

    end();
    begin();

    pageData = modelDataStorage.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_CHANGE_NAME));
    assertNotNull(pageData);
    assertEquals(1, pageData.getChildren().size());
    componentData = (ApplicationData<?>) pageData.getChildren().get(0);
    contentId = dataStorage.getId(componentData.getState());
    assertEquals(newContent, contentId);

    pageData = modelDataStorage.getPage(new PageKey(SITE_TYPE, SITE_NAME, PAGE_TO_KEEP_NAME));
    assertNotNull(pageData);
    assertEquals(1, pageData.getChildren().size());
    componentData = (ApplicationData<?>) pageData.getChildren().get(0);
    contentId = dataStorage.getId(componentData.getState());
    assertEquals(newContent, contentId);
  }

  protected void injectData() throws Exception {
    PortalConfig portalConfig = dataStorage.getPortalConfig(SITE_TYPE, SITE_NAME);
    if (portalConfig == null) {
      portalConfig = new PortalConfig(SITE_TYPE, SITE_NAME);
      dataStorage.create(portalConfig);
    }

    changeThisPage = createPage(PAGE_TO_CHANGE_NAME, oldContent);
    keepThisPage = createPage(PAGE_TO_KEEP_NAME, newContent);
  }

  protected void purgeData() {
    pageService.destroyPage(changeThisPage.getPageKey());
    pageService.destroyPage(keepThisPage.getPageKey());
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private Page createPage(String pageName, String contentId) throws Exception {
    Page page = new Page(SITE_TYPE, SITE_NAME, pageName);
    page.setAccessPermissions(new String[] { "Everyone" });
    ArrayList<ModelObject> children = new ArrayList<>();
    page.setChildren(children);
    Application<?> app = Application.createPortletApplication();
    children.add(app);
    app.setState(new TransientApplicationState(contentId, null));
    app.setTheme("theme");
    app.setTitle("title");
    app.setAccessPermissions(new String[] { "Everyone" });

    PageState pageState = Utils.toPageState(page);
    pageService.savePage(new PageContext(page.getPageKey(), pageState));
    dataStorage.save(page);

    return page;
  }
}
