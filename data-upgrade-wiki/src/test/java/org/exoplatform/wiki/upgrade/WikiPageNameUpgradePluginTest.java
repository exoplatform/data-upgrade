package org.exoplatform.wiki.upgrade;

import static org.junit.Assert.*;

import java.util.List;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.wiki.WikiException;
import org.exoplatform.wiki.mow.api.Page;
import org.exoplatform.wiki.mow.api.Wiki;
import org.exoplatform.wiki.service.DataStorage;
import org.exoplatform.wiki.service.WikiService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WikiPageNameUpgradePluginTest {

  protected PortalContainer         container;

  protected WikiService             wikiService;

  protected DataStorage             dataStorage;

  protected EntityManagerService    entityManagerService;

  private final String  oldNoteName = "oldName";

  private final String  newNoteName = "newName";

  private final String  newTitle = "newTitle";

  private final String  wikiType = "user";

  private final String  currentUser = "user";

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    wikiService = CommonsUtils.getService(WikiService.class);
    dataStorage = CommonsUtils.getService(DataStorage.class);
    entityManagerService = CommonsUtils.getService(EntityManagerService.class);
    begin();
  }

  @After
  public void tearDown() {
    end();
  }

  @Test
  public void testWikiMigration() throws WikiException {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("old.note.name");
    valueParam.setValue(oldNoteName);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.note.name");
    valueParam.setValue(newNoteName);
    initParams.addParameter(valueParam);

    valueParam = new ValueParam();
    valueParam.setName("new.note.title");
    valueParam.setValue(newTitle);
    initParams.addParameter(valueParam);

    String globalPortal = CommonsUtils.getService(UserPortalConfigService.class).getGlobalPortal();
    Identity identity = new Identity(currentUser);
    ConversationState.setCurrent(new ConversationState(identity));
    Wiki gWiki = wikiService.createWiki(wikiType,currentUser);

    List<Page> pages = wikiService.getPagesOfWiki(wikiType,currentUser);
    int initialWikiPages = pages.size();
    assertTrue(initialWikiPages > 0);
    Page page = new Page();
    page.setTitle(oldNoteName);
    page.setName(oldNoteName);

    wikiService.createPage(gWiki,pages.get(0).getName(),page);

    page = wikiService.getPageOfWikiByName(wikiType,currentUser,oldNoteName);
    assertNotNull(page);
    assertEquals(page.getName(),oldNoteName);

    WikiPageNameUpgradePlugin wikiPageNameUpgradePlugin = new WikiPageNameUpgradePlugin(container, entityManagerService, initParams);
    wikiPageNameUpgradePlugin.processUpgrade(null, null);

    page = wikiService.getPageOfWikiByName(wikiType,currentUser,oldNoteName);
    assertNull(page);
    page = wikiService.getPageOfWikiByName(wikiType,currentUser,newNoteName);
    assertNotNull(page);
    assertEquals(page.getName(),newNoteName);
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

}
