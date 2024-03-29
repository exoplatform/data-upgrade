package org.exoplatform.migration;

import org.exoplatform.component.test.AbstractKernelTest;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.mop.storage.PageStorage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration-local.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "org/exoplatform/portal/config/conf/configuration.xml"),
})
@RunWith(MockitoJUnitRunner.class)
public class AddCSSClassToPageTest extends AbstractKernelTest {

  private PageStorage pageStorage;

  @Before
  public void setUp() throws Exception {
    pageStorage = mock(PageStorage.class);
  }

  @Test
  public void processUpgrade() {
    InitParams initParams = new InitParams();
    ValueParam siteParam = new ValueParam();
    siteParam.setName("site-name");
    siteParam.setValue("dw");
    initParams.addParameter(siteParam);
    ValueParam pageNameParam = new ValueParam();
    pageNameParam.setName("page-name");
    pageNameParam.setValue("homepage");
    initParams.addParameter(pageNameParam);
    ValueParam containerIdParam = new ValueParam();
    containerIdParam.setName("container-id");
    containerIdParam.setValue("homePageContainer");
    initParams.addParameter(containerIdParam);
    ValueParam cssClassesParam = new ValueParam();
    cssClassesParam.setName("css-classes");
    cssClassesParam.setValue("testClass firstClass");
    initParams.addParameter(cssClassesParam);
    Page homePage = mock(Page.class);
    when(pageStorage.getPage(new PageKey(SiteType.PORTAL.getName(), "dw", "homepage"))).thenReturn(homePage);
    Container firstContainer = new Container();
    firstContainer.setId("homePageContainer");
    Container secondContainer = new Container();
    secondContainer.setId("footerContainer");
    when(homePage.getChildren()).thenReturn(new ArrayList<>(List.of(firstContainer, secondContainer)));
    AddCSSClassToPage addCSSClassToPage = new AddCSSClassToPage(pageStorage, initParams);
    addCSSClassToPage.processUpgrade("v1", "v2");
    verify(pageStorage, times(1)).save(homePage.build());
    for(ModelObject c : homePage.getChildren()) {
      if(((Container)c).getId().equals("homePageContainer")) {
        assertEquals("testClass firstClass", ((Container)c).getCssClass());
      } else {
        assertNull(((Container)c).getCssClass());
      }
    }
  }
}
