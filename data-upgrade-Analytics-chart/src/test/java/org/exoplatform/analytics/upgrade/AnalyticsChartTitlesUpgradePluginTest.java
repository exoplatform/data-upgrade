package org.exoplatform.analytics.upgrade;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.jdbc.entity.WindowEntity;
import org.exoplatform.portal.mop.jdbc.dao.WindowDAO;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.wiki.service.DataStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import javax.portlet.WindowStateException;

public class AnalyticsChartTitlesUpgradePluginTest {
  protected PortalContainer         container;

  protected DataStorage             dataStorage;

  protected EntityManagerService    entityManagerService;

  protected WindowDAO                windowDAO;

  private final String  currentUser = "user";

  private final String OLD_CHART_USERS_COUNT_TITLE = "Users count";

  private final String OLD_CHART_SPACES_COUNT_TITLE = "Spaces count";

  private final String OLD_CHART_ACTIVITIES_COUNT_TITLE = "Activities";

  private final String OLD_CHART_DISTINCT_LOGINS_TITLE = "Distinct logins";

  @Before
  public void setUp() {
    container = PortalContainer.getInstance();
    windowDAO  = container.getComponentInstanceOfType(WindowDAO.class);
    dataStorage = container.getComponentInstanceOfType(DataStorage.class);
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    begin();
  }
  @After
  public void tearDown() {
    end();
  }
  @Test
  public void testChartMigration() throws WindowStateException {
    InitParams initParams = new InitParams();
    Identity identity = new Identity(currentUser);
    ConversationState.setCurrent(new ConversationState(identity));
    String chart1customization = "test of chart migration with old title = " + OLD_CHART_USERS_COUNT_TITLE ;
    String chart2customization = "test of chart migration with old title = " + OLD_CHART_SPACES_COUNT_TITLE ;
    String chart3customization = "test of chart migration with old title = " + OLD_CHART_ACTIVITIES_COUNT_TITLE ;
    String chart4customization = "test of chart migration with old title = " + OLD_CHART_DISTINCT_LOGINS_TITLE;
    WindowEntity app1 = createWindow("win1" ,chart1customization.getBytes());
    WindowEntity app2 = createWindow("win2" ,chart2customization.getBytes());
    WindowEntity app3 = createWindow("win3" ,chart3customization.getBytes());
    WindowEntity app4 = createWindow("win4" ,chart4customization.getBytes());
    List<WindowEntity> windowEntityList = new ArrayList<>();
    windowEntityList.add(app1);
    windowEntityList.add(app2);
    windowEntityList.add(app3);
    windowEntityList.add(app4);
    windowDAO.createAll(windowEntityList);

    assertTrue(new String(app1.getCustomization(),StandardCharsets.UTF_8).contains(OLD_CHART_USERS_COUNT_TITLE) && new String(app2.getCustomization(),StandardCharsets.UTF_8).contains(OLD_CHART_SPACES_COUNT_TITLE) &&
                   new String(app3.getCustomization(),StandardCharsets.UTF_8).contains(OLD_CHART_ACTIVITIES_COUNT_TITLE) && new String(app4.getCustomization(),StandardCharsets.UTF_8).contains(OLD_CHART_DISTINCT_LOGINS_TITLE));

    AnalyticsChartTitlesUpgradePlugin analyticsChartTitlesUpgradePlugin = new AnalyticsChartTitlesUpgradePlugin(container,entityManagerService,initParams);
    analyticsChartTitlesUpgradePlugin.processUpgrade(null,null);

    assertTrue(analyticsChartTitlesUpgradePlugin.getChartUpdatedCount() == 4 );
  }
  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }
  protected void end() {
    RequestLifeCycle.end();
  }
  private WindowEntity createWindow(String title,byte[] customization) {
    WindowEntity window = new WindowEntity();
    window.setTitle(title);
    window.setContentId("analytics/AnalyticsPortlet");
    window.setCustomization(customization);
    return window;
  }
}
