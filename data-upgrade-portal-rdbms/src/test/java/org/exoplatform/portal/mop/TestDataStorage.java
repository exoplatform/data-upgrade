/**
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.mop;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.gatein.common.transaction.JTAUserTransactionLifecycleService;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.AbstractJCRImplTest;
import org.exoplatform.portal.config.*;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.mop.*;
import org.exoplatform.portal.mop.navigation.*;
import org.exoplatform.portal.mop.page.*;
import org.exoplatform.portal.pom.data.ModelChange;
import org.exoplatform.portal.pom.spi.portlet.Portlet;
import org.exoplatform.portal.pom.spi.portlet.PortletBuilder;
import org.exoplatform.services.listener.*;
import org.exoplatform.services.organization.*;

import junit.framework.AssertionFailedError;

/**
 * Created by The eXo Platform SARL Author : Tung Pham thanhtungty@gmail.com Nov
 * 13, 2007
 */
public class TestDataStorage extends AbstractJCRImplTest {

  /** . */
  private DataStorage                        storage_;

  /** . */
  private PageService                        pageService;

  /** . */
  private NavigationService                  navService;

  /** . */
  private LinkedList<Event>                  events;

  /** . */
  private OrganizationService                org;

  private JTAUserTransactionLifecycleService jtaUserTransactionLifecycleService;

  public TestDataStorage(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    Listener listener = new Listener() {
      @Override
      public void onEvent(Event event) throws Exception {
        events.add(event);
      }
    };
    events = new LinkedList<>();

    //
    super.setUp();
    storage_ = getService(DataStorage.class);
    pageService = getService(PageService.class);
    navService = getService(NavigationService.class);
    org = getService(OrganizationService.class);
    jtaUserTransactionLifecycleService = getService(JTAUserTransactionLifecycleService.class);

    //
    ListenerService listenerService = getService(ListenerService.class);
    listenerService.addListener(EventType.PAGE_CREATED, listener);
    listenerService.addListener(EventType.PAGE_DESTROYED, listener);
    listenerService.addListener(DataStorage.PAGE_UPDATED, listener);
    listenerService.addListener(EventType.NAVIGATION_CREATED, listener);
    listenerService.addListener(EventType.NAVIGATION_DESTROYED, listener);
    listenerService.addListener(EventType.NAVIGATION_UPDATED, listener);
    listenerService.addListener(DataStorage.PORTAL_CONFIG_CREATED, listener);
    listenerService.addListener(DataStorage.PORTAL_CONFIG_UPDATED, listener);
    listenerService.addListener(DataStorage.PORTAL_CONFIG_REMOVED, listener);

    //
    begin();
  }

  protected void tearDown() throws Exception {
    end();
    super.tearDown();
  }

  private void assertPageFound(int offset,
                               int limit,
                               SiteType siteType,
                               String siteName,
                               String pageName,
                               String title,
                               String expectedPage) {
    QueryResult<PageContext> res = pageService.findPages(offset, limit, siteType, siteName, pageName, title);
    assertEquals(1, res.getSize());
    assertEquals(expectedPage, res.iterator().next().getKey().format());
  }

  private void assertPageNotFound(int offset, int limit, SiteType siteType, String siteName, String pageName, String title) {
    QueryResult<PageContext> res = pageService.findPages(offset, limit, siteType, siteName, pageName, title);
    assertEquals(0, res.getSize());
  }

  public void testCreatePortal() throws Exception {
    String label = "portal_foo";
    String description = "This is new portal for testing";
    PortalConfig portal = new PortalConfig();
    portal.setType("portal");
    portal.setName("foo1");
    portal.setLocale("en");
    portal.setLabel(label);
    portal.setDescription(description);
    portal.setAccessPermissions(new String[] { UserACL.EVERYONE });

    //
    storage_.create(portal);
    assertEquals(1, events.size());
    portal = storage_.getPortalConfig(portal.getName());
    assertNotNull(portal);
    assertEquals("portal", portal.getType());
    assertEquals("foo1", portal.getName());
    assertEquals(label, portal.getLabel());
    assertEquals(description, portal.getDescription());
  }

  public void testPortalConfigSave() throws Exception {
    PortalConfig portal = storage_.getPortalConfig("portal", "test");
    assertNotNull(portal);

    //
    portal.setLocale("vietnam");
    storage_.save(portal);
    assertEquals(1, events.size());
    //
    portal = storage_.getPortalConfig("portal", "test");
    assertNotNull(portal);
    assertEquals("vietnam", portal.getLocale());
  }

  public void testPortalConfigRemove() throws Exception {
    PortalConfig portal = storage_.getPortalConfig("portal", "test");
    assertNotNull(portal);

    storage_.remove(portal);
    assertEquals(1, events.size());
    assertNull(storage_.getPortalConfig("portal", "test"));

    try {
      // Trying to remove non existing a portal config
      storage_.remove(portal);
      fail("was expecting a NoSuchDataException");
    } catch (NoSuchDataException e) {

    }
  }

  public void testSavePage() throws Exception {
    PortalConfig portal = new PortalConfig();
    portal.setType("portal");
    portal.setName("test2");
    portal.setLocale("en");
    portal.setLabel("Test 2");
    portal.setDescription("Test 2");
    portal.setAccessPermissions(new String[] { UserACL.EVERYONE });

    //
    storage_.create(portal);
    assertEquals(1, events.size());

    Page page = new Page();
    page.setOwnerType(PortalConfig.PORTAL_TYPE);
    page.setOwnerId(portal.getName());
    page.setName("foo");
    page.setShowMaxWindow(false);

    //
    try {
      storage_.save(page);
      fail();
    } catch (NoSuchDataException e) {
    }
    pageService.savePage(new PageContext(page.getPageKey(), null));
    assertEquals(2, events.size());

    //
    PageContext pageContext = pageService.loadPage(page.getPageKey());
    pageContext.setState(pageContext.getState().builder().displayName("MyTitle").showMaxWindow(true).build());
    pageService.savePage(pageContext);

    //
    Page page2 = storage_.getPage(page.getPageId());
    page2.setTitle("MyTitle2");
    page2.setShowMaxWindow(true);
    storage_.save(page2);
    assertEquals(3, events.size());

    page2 = storage_.getPage(page.getPageId());
    assertNotNull(page2);
    assertEquals("portal::" + portal.getName() + "::foo", page2.getPageId());
    assertEquals("portal", page2.getOwnerType());
    assertEquals(portal.getName(), page2.getOwnerId());
    assertEquals("foo", page2.getName());
    assertNull(page2.getTitle());
    assertEquals(0, page2.getChildren().size());
    assertEquals(false, page2.isShowMaxWindow());

    pageContext = pageService.loadPage(page.getPageKey());
    assertEquals("MyTitle", pageContext.getState().getDisplayName());
    assertEquals(true, pageContext.getState().getShowMaxWindow());
  }

  public void testChangingPortletThemeInPage() throws Exception {
    Page page;
    Application<?> app;

    page = storage_.getPage("portal::classic::homepage");
    app = (Application<?>) page.getChildren().get(0);
    assertEquals(1, page.getChildren().size());
    app.setTheme("Theme1");
    storage_.save(page);

    page = storage_.getPage("portal::classic::homepage");
    app = (Application<?>) page.getChildren().get(0);
    assertEquals("Theme1", app.getTheme());
    app.setTheme("Theme2");
    storage_.save(page);

    page = storage_.getPage("portal::classic::homepage");
    app = (Application<?>) page.getChildren().get(0);
    assertEquals("Theme2", app.getTheme());
  }

  public void testPageRemove() throws Exception {
    Page page = storage_.getPage("portal::test::test1");
    assertNotNull(page);

    //
    try {
      storage_.remove(page);
      fail();
    } catch (UnsupportedOperationException e) {
    }
  }

  public void testWindowMove1() throws Exception {
    Page page = storage_.getPage("portal::test::test4");
    Application<?> a1 = (Application<?>) page.getChildren().get(0);
    Container a2 = (Container) page.getChildren().get(1);
    Application<?> a3 = (Application<?>) a2.getChildren().get(0);
    Application<?> a4 = (Application<?>) a2.getChildren().remove(1);
    page.getChildren().add(1, a4);
    List<ModelChange> changes = storage_.save(page);

    //
    page = storage_.getPage("portal::test::test4");
    assertEquals(3, page.getChildren().size());
    Application<?> c1 = (Application<?>) page.getChildren().get(0);
    assertEquals(a1.getStorageId(), c1.getStorageId());
    Application<?> c2 = (Application<?>) page.getChildren().get(1);
    assertEquals(a4.getStorageId(), c2.getStorageId());
    Container c3 = (Container) page.getChildren().get(2);
    assertEquals(a2.getStorageId(), c3.getStorageId());
    assertEquals(1, c3.getChildren().size());
    Application<?> c4 = (Application<?>) c3.getChildren().get(0);
    assertEquals(a3.getStorageId(), c4.getStorageId());

    //
    assertEquals(6, changes.size());
    ModelChange.Update ch1 = (ModelChange.Update) changes.get(0);
    assertEquals(page.getStorageId(), ch1.getObject().getStorageId());
    ModelChange.Update ch2 = (ModelChange.Update) changes.get(1);
    assertEquals(a1.getStorageId(), ch2.getObject().getStorageId());
    ModelChange.Move ch3 = (ModelChange.Move) changes.get(2);
    // assertEquals(a2.getStorageId(), ch3.getSrcId());
    // assertEquals(page.getStorageId(), ch3.getDstId());
    assertEquals(a4.getStorageId(), ch3.getId());
    ModelChange.Update ch4 = (ModelChange.Update) changes.get(3);
    assertEquals(a4.getStorageId(), ch4.getObject().getStorageId());
    ModelChange.Update ch5 = (ModelChange.Update) changes.get(4);
    assertEquals(a2.getStorageId(), ch5.getObject().getStorageId());
    ModelChange.Update ch6 = (ModelChange.Update) changes.get(5);
    assertEquals(a3.getStorageId(), ch6.getObject().getStorageId());
  }

  public void testWindowMove2() throws Exception {
    Page page = storage_.getPage("portal::test::test3");
    Container container = new Container();
    Application application = (Application) page.getChildren().remove(0);
    container.getChildren().add(application);
    page.getChildren().add(container);

    //
    storage_.save(page);

    //
    Page page2 = storage_.getPage("portal::test::test3");

    //
    assertEquals(1, page2.getChildren().size());
    Container container2 = (Container) page2.getChildren().get(0);
    assertEquals(1, page2.getChildren().size());
    Application application2 = (Application) container2.getChildren().get(0);
    assertEquals(application2.getStorageId(), application.getStorageId());
  }

  // Test for issue GTNPORTAL-2074
  public void testWindowMove3() throws Exception {
    assertNull(storage_.getPage("portal::test::testWindowMove3"));

    Page page = new Page();
    page.setOwnerType(PortalConfig.PORTAL_TYPE);
    page.setOwnerId("test");
    page.setName("testWindowMove3");
    Application app1 = new Application(ApplicationType.PORTLET);
    app1.setState(new TransientApplicationState<Portlet>());
    Application app2 = new Application(ApplicationType.PORTLET);
    app2.setState(new TransientApplicationState<Portlet>());
    Container parentOfApp2 = new Container();
    parentOfApp2.getChildren().add(app2);

    page.getChildren().add(app1);
    page.getChildren().add(parentOfApp2);

    pageService.savePage(new PageContext(page.getPageKey(), null));
    storage_.save(page);

    Page page2 = storage_.getPage("portal::test::testWindowMove3");
    assertNotNull(page2);

    assertTrue(page2.getChildren().get(1) instanceof Container);
    Container container = (Container) page2.getChildren().get(1);

    assertTrue(container.getChildren().get(0) instanceof Application);
    Application persistedApp2 = (Application) container.getChildren().remove(0);

    Container transientContainer = new Container();
    transientContainer.getChildren().add(persistedApp2);

    page2.getChildren().add(transientContainer);

    storage_.save(page2);

    Page page3 = storage_.getPage("portal::test::testWindowMove3");

    assertEquals(container.getStorageId(), page3.getChildren().get(1).getStorageId());

    assertTrue(page3.getChildren().get(2) instanceof Container);
    Container formerTransientCont = (Container) page3.getChildren().get(2);
    assertEquals(1, formerTransientCont.getChildren().size());
    assertTrue(formerTransientCont.getChildren().get(0) instanceof Application);

    assertEquals(persistedApp2.getStorageId(), formerTransientCont.getChildren().get(0).getStorageId());
  }

  /**
   * Test that setting a page reference to null will actually remove the page
   * reference from the PageNode
   *
   * @throws Exception
   */
  public void testNullPageReferenceDeletes() throws Exception {
    // create portal
    PortalConfig portal = new PortalConfig();
    portal.setName("foo");
    portal.setLocale("en");
    portal.setAccessPermissions(new String[] { UserACL.EVERYONE });
    storage_.create(portal);

    // create page
    Page page = new Page();
    page.setOwnerType(PortalConfig.PORTAL_TYPE);
    page.setOwnerId("test");
    page.setName("foo");
    pageService.savePage(new PageContext(page.getPageKey(), null));

    // create a new page navigation and add node
    NavigationContext nav = new NavigationContext(SiteKey.portal("foo"), new NavigationState(0));
    navService.saveNavigation(nav);
    NodeContext<?> node = navService.loadNode(NodeModel.SELF_MODEL, nav, Scope.CHILDREN, null);
    NodeContext<?> test = node.add(null, "testPage");
    test.setState(test.getState().builder().pageRef(page.getPageKey()).build());
    navService.saveNode(node, null);

    // get the page reference from the created page and check that it exists
    NodeContext<?> pageNavigationWithPageReference = navService.loadNode(NodeModel.SELF_MODEL, nav, Scope.CHILDREN, null);
    assertNotNull("Expected page reference should not be null.",
                  pageNavigationWithPageReference.get(0)
                                                 .getState()
                                                 .getPageRef());

    // set the page reference to null and save.
    test.setState(test.getState().builder().pageRef(null).build());
    navService.saveNode(node, null);

    // check that setting the page reference to null actually removes the page
    // reference
    NodeContext<?> pageNavigationWithoutPageReference = navService
                                                                  .loadNode(NodeModel.SELF_MODEL, nav, Scope.CHILDREN, null);
    assertNull("Expected page reference should be null.", pageNavigationWithoutPageReference.get(0).getState().getPageRef());
  }

  public void testWindowScopedPortletPreferences() throws Exception {
    Page page = new Page();
    page.setPageId("portal::test::foo");
    TransientApplicationState<Portlet> state = new TransientApplicationState<Portlet>("web/BannerPortlet",
                                                                                      new PortletBuilder().add("template", "bar")
                                                                                                          .build());
    Application<Portlet> app = Application.createPortletApplication();
    app.setState(state);
    page.getChildren().add(app);
    pageService.savePage(new PageContext(page.getPageKey(), null));
    storage_.save(page);
    page = storage_.getPage(page.getPageId());
    app = (Application<Portlet>) page.getChildren().get(0);
    assertEquals("web/BannerPortlet", storage_.getId(app.getState()));
  }

  public void testPageMerge() throws Exception {
    Page page = storage_.getPage("portal::test::test4");

    String app1Id = page.getChildren().get(0).getStorageId();
    Container container = (Container) page.getChildren().get(1);
    String containerId = container.getStorageId();
    String app2Id = container.getChildren().get(0).getStorageId();
    String app3Id = container.getChildren().get(1).getStorageId();

    // Add an application
    Application<Portlet> groovyApp = Application.createPortletApplication();
    ApplicationState<Portlet> state = new TransientApplicationState<Portlet>("web/GroovyPortlet");
    groovyApp.setState(state);
    ((Container) page.getChildren().get(1)).getChildren().add(1, groovyApp);

    // Save
    List<ModelChange> changes = storage_.save(page);
    assertEquals(6, changes.size());
    ModelChange.Update c0 = (ModelChange.Update) changes.get(0);
    assertSame(page.getStorageId(), c0.getObject().getStorageId());
    ModelChange.Update c1 = (ModelChange.Update) changes.get(1);
    assertSame(page.getChildren().get(0).getStorageId(), c1.getObject().getStorageId());
    ModelChange.Update c2 = (ModelChange.Update) changes.get(2);
    assertSame(page.getChildren().get(1).getStorageId(), c2.getObject().getStorageId());
    ModelChange.Update c3 = (ModelChange.Update) changes.get(3);
    assertSame(container.getChildren().get(0).getStorageId(), c3.getObject().getStorageId());
    ModelChange.Create c4 = (ModelChange.Create) changes.get(4);
    assertSame(container.getChildren().get(1).getStorageId(), c4.getObject().getStorageId());
    ModelChange.Update c5 = (ModelChange.Update) changes.get(5);
    assertSame(container.getChildren().get(2).getStorageId(), c5.getObject().getStorageId());

    // Check it is existing at the correct location
    // and also that the ids are still the same
    page = storage_.getPage("portal::test::test4");
    assertEquals(2, page.getChildren().size());
    // assertEquals(PortletState.create("portal#test:/web/BannerPortlet/banner"),
    // ((Application)page.getChildren().get(0)).getInstanceState());
    assertEquals(app1Id, page.getChildren().get(0).getStorageId());
    container = (Container) page.getChildren().get(1);
    assertEquals(3, container.getChildren().size());
    assertEquals(containerId, container.getStorageId());
    // assertEquals(PortletState.create("portal#test:/web/BannerPortlet/banner"),
    // ((Application)container.getChildren().get(0)).getInstanceState());
    assertEquals(app2Id, container.getChildren().get(0).getStorageId());
    // assertEquals(PortletState.create("portal#test:/web/GroovyPortlet/groovyportlet"),
    // ((Application)container.getChildren().get(1)).getInstanceState());
    assertNotNull(container.getChildren().get(0).getStorageId());
    // assertEquals(PortletState.create("portal#test:/web/FooterPortlet/footer"),
    // ((Application)container.getChildren().get(2)).getInstanceState());
    assertEquals(app3Id, container.getChildren().get(2).getStorageId());

    // Now remove the element
    container.getChildren().remove(1);
    storage_.save(page);

    // Check it is removed
    // and also that the ids are still the same
    page = storage_.getPage("portal::test::test4");
    assertEquals(2, page.getChildren().size());
    // assertEquals(PortletState.create("portal#test:/web/BannerPortlet/banner"),
    // ((Application)page.getChildren().get(0)).getInstanceState());
    assertEquals(app1Id, page.getChildren().get(0).getStorageId());
    container = (Container) page.getChildren().get(1);
    assertEquals(2, container.getChildren().size());
    assertEquals(containerId, container.getStorageId());
    // assertEquals(PortletState.create("portal#test:/web/BannerPortlet/banner"),
    // ((Application)container.getChildren().get(0)).getInstanceState());
    assertEquals(app2Id, container.getChildren().get(0).getStorageId());
    // assertEquals(PortletState.create("portal#test:/web/FooterPortlet/footer"),
    // ((Application)container.getChildren().get(1)).getInstanceState());
    assertEquals(app3Id, container.getChildren().get(1).getStorageId());
  }

  public void testClone() throws Exception {
    pageService.clone(PageKey.parse("portal::test::test4"), PageKey.parse("portal::test::_test4"));

    // Get cloned page
    Page clone = storage_.getPage("portal::test::_test4");
    assertEquals(2, clone.getChildren().size());
    Application<Portlet> banner1 = (Application<Portlet>) clone.getChildren().get(0);
    ApplicationState<Portlet> instanceId = banner1.getState();

    // Check instance id format
    assertEquals("web/BannerPortlet", storage_.getId(banner1.getState()));

    // Check state
    Portlet pagePrefs = storage_.load(instanceId, ApplicationType.PORTLET);
    assertEquals(new PortletBuilder().add("template", "par:/groovy/groovy/webui/component/UIBannerPortlet.gtmpl").build(),
                 pagePrefs);

    // Update page prefs
    pagePrefs.setValue("template", "foo");
    storage_.save(instanceId, pagePrefs);

    // Check that page prefs have changed
    pagePrefs = storage_.load(instanceId, ApplicationType.PORTLET);
    assertEquals(new PortletBuilder().add("template", "foo").build(), pagePrefs);

    // Now check the container
    Container container = (Container) clone.getChildren().get(1);
    assertEquals(2, container.getChildren().size());

    //
    Page srcPage = storage_.getPage("portal::test::test4");
    PageContext srcPageContext = pageService.loadPage(srcPage.getPageKey());
    srcPageContext.setState(srcPageContext.getState().builder().editPermission("Administrator").build());
    pageService.savePage(srcPageContext);

    //
    Application<Portlet> portlet = (Application<Portlet>) srcPage.getChildren().get(0);
    portlet.setDescription("NewPortlet");
    ArrayList<ModelObject> modelObject = srcPage.getChildren();
    modelObject.set(0, portlet);
    srcPage.setChildren(modelObject);
    storage_.save(srcPage);

    //
    PageKey dstKey = PageKey.parse(srcPage.getOwnerType() + "::" + srcPage.getOwnerId() + "::" + "_PageTest1234");
    PageContext dstPageContext = pageService.clone(srcPageContext.getKey(), dstKey);
    Page dstPage = storage_.getPage(dstKey.format());
    Application<Portlet> portlet1 = (Application<Portlet>) dstPage.getChildren().get(0);

    // Check src's edit permission and dst's edit permission
    assertNotNull(dstPageContext.getState().getEditPermission());
    assertEquals(srcPageContext.getState().getEditPermission(), dstPageContext.getState().getEditPermission());

    // Check src's children and dst's children
    assertNotNull(portlet1.getDescription());
    assertEquals(portlet.getDescription(), portlet1.getDescription());
  }

  public void testGetAllPortalNames() throws Exception {
    testGetAllSiteNames("portal", "getAllPortalNames");
  }

  public void testGetAllGroupNames() throws Exception {
    testGetAllSiteNames("group", "getAllGroupNames");
  }

  private void testGetAllSiteNames(String siteType, final String methodName) throws Exception {
    final List<String> names = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);

    // Create new portal
    storage_.create(new PortalConfig(siteType, "testGetAllSiteNames"));

    // Test during tx we see the good names
    List<String> transientNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
    assertTrue("Was expecting " + transientNames + " to contain " + names, transientNames.containsAll(names));
    transientNames.removeAll(names);
    assertEquals(Collections.singletonList("testGetAllSiteNames"), transientNames);

    // Test we have not seen anything yet outside of tx
    final CountDownLatch addSync = new CountDownLatch(1);
    final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
    new Thread() {
      @Override
      public void run() {
        begin();
        ExoContainerContext.setCurrentContainer(getContainer());
        try {
          List<String> isolatedNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
          assertEquals(new HashSet<String>(names), new HashSet<String>(isolatedNames));
        } catch (Throwable t) {
          error.set(t);
        } finally {
          addSync.countDown();
          end();
        }
      }
    }.start();

    //
    addSync.await();
    if (error.get() != null) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(error.get());
      throw afe;
    }

    // Now commit tx
    end();

    // We test we observe the change
    begin();
    List<String> afterNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
    assertTrue(afterNames.containsAll(names));
    afterNames.removeAll(names);
    assertEquals(Collections.singletonList("testGetAllSiteNames"), afterNames);

    // Then we remove the newly created portal
    storage_.remove(new PortalConfig(siteType, "testGetAllSiteNames"));

    // Test we are syeing the transient change
    transientNames.clear();
    transientNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
    assertEquals(names, transientNames);

    // Test we have not seen anything yet outside of tx
    error.set(null);
    final CountDownLatch removeSync = new CountDownLatch(1);
    new Thread() {
      public void run() {
        begin();
        ExoContainerContext.setCurrentContainer(getContainer());
        try {
          List<String> isolatedNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
          assertTrue("Was expecting " + isolatedNames + " to contain " + names, isolatedNames.containsAll(names));
          isolatedNames.removeAll(names);
          assertEquals(Collections.emptyList(), isolatedNames);
        } catch (Throwable t) {
          error.set(t);
        } finally {
          removeSync.countDown();
          end();
        }
      }
    }.start();

    //
    removeSync.await();
    if (error.get() != null) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(error.get());
      throw afe;
    }

    //
    end();

    // Now test it is still removed
    begin();
    afterNames = (List<String>) storage_.getClass().getMethod(methodName).invoke(storage_);
    assertEquals(new HashSet<String>(names), new HashSet<String>(afterNames));
  }

  public void testSiteScopedPreferences() throws Exception {
    Page page = storage_.getPage("portal::test::test4");
    Application<Portlet> app = (Application<Portlet>) page.getChildren().get(0);
    PersistentApplicationState<Portlet> state = (PersistentApplicationState) app.getState();

    //
    Portlet prefs = storage_.load(state, ApplicationType.PORTLET);

    //
    prefs.setValue("template", "someanothervalue");
    storage_.save(state, prefs);

    //
    prefs = storage_.load(state, ApplicationType.PORTLET);
    assertNotNull(prefs);
    assertEquals(new PortletBuilder().add("template", "someanothervalue").build(), prefs);
  }

  public void testNullPreferenceValue() throws Exception {
    Page page = storage_.getPage("portal::test::test4");
    Application<Portlet> app = (Application<Portlet>) page.getChildren().get(0);
    PersistentApplicationState<Portlet> state = (PersistentApplicationState) app.getState();

    //
    Portlet prefs = new Portlet();
    prefs.setValue("template", "initialvalue");
    storage_.save(state, prefs);

    //
    prefs.setValue("template", null);
    storage_.save(state, prefs);

    //
    prefs = storage_.load(state, ApplicationType.PORTLET);
    assertNotNull(prefs);
    assertEquals(new PortletBuilder().add("template", "").build(), prefs);
  }

  public void testSiteLayout() throws Exception {
    PortalConfig pConfig = storage_.getPortalConfig(PortalConfig.PORTAL_TYPE, "classic");
    assertNotNull(pConfig);
    assertNotNull("The Group layout of " + pConfig.getName() + " is null", pConfig.getPortalLayout());

    pConfig = storage_.getPortalConfig(PortalConfig.GROUP_TYPE, "/platform/administrators");
    assertNotNull(pConfig);
    assertNotNull("The Group layout of " + pConfig.getName() + " is null", pConfig.getPortalLayout());
    assertTrue(pConfig.getPortalLayout().getChildren() != null && pConfig.getPortalLayout().getChildren().size() > 1);
    pConfig.getPortalLayout().getChildren().clear();
    storage_.save(pConfig);

    pConfig = storage_.getPortalConfig(PortalConfig.GROUP_TYPE, "/platform/administrators");
    assertNotNull(pConfig);
    assertNotNull("The Group layout of " + pConfig.getName() + " is null", pConfig.getPortalLayout());
    assertTrue(pConfig.getPortalLayout().getChildren() != null && pConfig.getPortalLayout().getChildren().size() == 0);

    pConfig = storage_.getPortalConfig(PortalConfig.USER_TYPE, "root");
    assertNotNull(pConfig);
    assertNotNull("The User layout of " + pConfig.getName() + " is null", pConfig.getPortalLayout());

    pConfig = storage_.getPortalConfig(PortalConfig.USER_TYPE, "mary");
    assertNotNull(pConfig);
    assertNotNull("The User layout of " + pConfig.getName() + " is null", pConfig.getPortalLayout());
  }

  public void testGroupLayout() throws Exception {
    GroupHandler groupHandler = org.getGroupHandler();
    Group group = groupHandler.findGroupById("groupTest");
    assertNull(group);

    group = groupHandler.createGroupInstance();
    group.setGroupName("groupTest");
    group.setLabel("group label");

    groupHandler.addChild(null, group, true);

    group = groupHandler.findGroupById("/groupTest");
    assertNotNull(group);

    PortalConfig pConfig = storage_.getPortalConfig(PortalConfig.GROUP_TYPE, "/groupTest");
    assertNotNull("the Group's PortalConfig is not null", pConfig);
    assertTrue(pConfig.getPortalLayout().getChildren() == null || pConfig.getPortalLayout().getChildren().size() == 4);

    /**
     * We need to remove the /groupTest from the groupHandler as the handler is
     * shared between the tests and can cause other tests to fail. TODO: make
     * the tests fully independent
     */
    groupHandler.removeGroup(group, false);
    group = groupHandler.findGroupById("/groupTest");
    assertNull(group);
  }

  public void testGroupNavigation() throws Exception {
    GroupHandler groupHandler = org.getGroupHandler();
    Group group = groupHandler.createGroupInstance();
    group.setGroupName("testGroupNavigation");
    group.setLabel("testGroupNavigation");

    groupHandler.addChild(null, group, true);

    SiteKey key = SiteKey.group(group.getId());
    navService.saveNavigation(new NavigationContext(key, new NavigationState(0)));
    assertNotNull(navService.loadNavigation(key));

    // Remove group
    groupHandler.removeGroup(group, true);

    // Group navigations is removed after remove group
    assertNull(navService.loadNavigation(key));
  }

  public void testUserLayout() throws Exception {
    UserHandler userHandler = org.getUserHandler();
    User user = userHandler.findUserByName("testing");
    assertNull(user);

    user = userHandler.createUserInstance("testing");
    user.setEmail("testing@gmaild.com");
    user.setFirstName("test firstname");
    user.setLastName("test lastname");
    user.setPassword("123456");

    userHandler.createUser(user, true);

    user = userHandler.findUserByName("testing");
    assertNotNull(user);

    PortalConfig pConfig = storage_.getPortalConfig(PortalConfig.USER_TYPE, "testing");
    assertNotNull("the User's PortalConfig is not null", pConfig);
  }

  public void testJTA() throws Exception {
    jtaUserTransactionLifecycleService.beginJTATransaction();
    PageContext pageContext = null;
    try {
      Page page = new Page();
      page.setPageId("portal::test::searchedpage2");
      pageService.savePage(new PageContext(page.getPageKey(), null));

      pageContext = pageService.loadPage(page.getPageKey());
      pageContext.setState(pageContext.getState().builder().displayName("Juuu2 Ziii2").build());
      pageService.savePage(pageContext);

      assertPageFound(0, 10, null, null, null, "Juuu2 Ziii2", "portal::test::searchedpage2");
    } finally {
      jtaUserTransactionLifecycleService.finishJTATransaction();
    }

    jtaUserTransactionLifecycleService.beginJTATransaction();
    try {
      pageService.destroyPage(pageContext.getKey());
      assertPageNotFound(0, 10, null, null, null, "Juuu2 Ziii2");
    } finally {
      jtaUserTransactionLifecycleService.finishJTATransaction();
    }
  }

}
