package org.exoplatform.migration;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.Container;
import org.exoplatform.portal.config.model.ModelObject;
import org.exoplatform.portal.config.model.Page;
import org.exoplatform.portal.mop.SiteType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AddCSSClassToPageTest {

  private DataStorage pageStorage;

  @Before
  public void setUp() throws Exception {
    pageStorage = mock(DataStorage.class);
  }

  @Test
  public void processUpgrade() throws Exception {
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
    when(pageStorage.getPage(SiteType.PORTAL.getName() + "::" + "dw" + "::" + "homepage")).thenReturn(homePage);
    Container firstContainer = new Container();
    firstContainer.setId("homePageContainer");
    Container secondContainer = new Container();
    secondContainer.setId("footerContainer");
    when(homePage.getChildren()).thenReturn(new ArrayList<>(List.of(firstContainer, secondContainer)));
    AddCSSClassToPage addCSSClassToPage = new AddCSSClassToPage(pageStorage, initParams);
    addCSSClassToPage.processUpgrade("v1", "v2");
    verify(pageStorage, times(1)).save(homePage);
    for(ModelObject c : homePage.getChildren()) {
      if(((Container)c).getId().equals("homePageContainer")) {
        assertEquals("testClass firstClass", ((Container)c).getCssClass());
      } else {
        assertNull(((Container)c).getCssClass());
      }
    }
  }
}
