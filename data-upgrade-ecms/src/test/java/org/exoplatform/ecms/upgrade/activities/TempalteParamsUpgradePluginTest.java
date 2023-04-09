package org.exoplatform.ecms.upgrade.activities;


import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TempalteParamsUpgradePluginTest {

  @Mock
  private  PortalContainer container;

  @Mock
  private  EntityManagerService entityManagerService;

  @Mock
  private EntityTransaction entityTransaction;

  @Mock
  private EntityManager entityManager;

  @Mock
  private Query query;


  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(entityTransaction);
  }


  @Test
  public void templateParamsUpgradePluginTest(){

    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    valueParam.setName("new.template.params.key");
    valueParam.setValue("WORKSPACE");
    initParams.addParameter(valueParam);
    valueParam.setName("old.template.params.key");
    valueParam.setValue("WORKSPACE  ");
    initParams.addParameter(valueParam);

    TemplateParamsUpgradePlugin templateParamsUpgradePlugin = new TemplateParamsUpgradePlugin(container,entityManagerService,initParams);

    // Mock the EntityManager and Query
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.executeUpdate()).thenReturn(1);

    // Call the process upgrade
    templateParamsUpgradePlugin.processUpgrade(null,null);

    // Verify that the EntityManager was called with the correct SQL string
    verify(entityManager).createNativeQuery(
        "UPDATE SOC_ACTIVITY_TEMPLATE_PARAMS SET TEMPLATE_PARAM_KEY = TRIM(:newTemplateParamskey) WHERE TEMPLATE_PARAM_KEY LIKE :oldTemplateParamskey");

    // Verify that the Query was called to execute the update
    verify(query).executeUpdate();

    // Verify that the correct count was returned
    assertEquals(1, templateParamsUpgradePlugin.getTemplatePramasUpdatedCount());
  }

}
