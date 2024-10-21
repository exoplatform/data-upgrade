package org.exoplatform.news.upgrade;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;
import org.exoplatform.services.attachments.storage.AttachmentStorage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ArticleAttachmentsUpgradePluginTest {

  @Mock
  private SettingService                  settingService;

  @Mock
  private NoteService                     noteService;

  @Mock
  private EntityManagerService            entityManagerService;

  @Mock
  private AttachmentStorage               attachmentStorage;

  @Mock
  private EntityManager                   entityManager;

  @Mock
  private EntityTransaction               transaction;

  @Mock
  PortalContainer container;

  private ArticleAttachmentsUpgradePlugin articleAttachmentsUpgradePlugin;

  private static final String             ARTICLES_UPGRADE_EXECUTED_KEY = "articlesUpgradeExecuted";

  @Before
  public void setUp() {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    articleAttachmentsUpgradePlugin = new ArticleAttachmentsUpgradePlugin(initParams,
                                                                          entityManagerService,
                                                                          settingService,
                                                                          noteService,
                                                                          attachmentStorage,container);

    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
    when(entityManager.getTransaction()).thenReturn(transaction);
  }

  @Test
  public void shouldNotProceedToUpgrade_WhenSettingIsFalse() {

    SettingValue settingValue = mock(SettingValue.class);
    when(settingValue.getValue()).thenReturn("false");
    when(settingService.get(any(), any(), anyString())).thenReturn(settingValue, null);

    boolean result = articleAttachmentsUpgradePlugin.shouldProceedToUpgrade("6.5.4", "7.0.0");

    // Validate the result
    assertFalse(result);
    verify(settingService, times(1)).get(any(), any(), eq(ARTICLES_UPGRADE_EXECUTED_KEY));
  }

  @Test
    public void processUpgrade_ShouldLinkAttachmentsAndCommitTransaction() {

        when(entityManager.getTransaction().isActive()).thenReturn(false).thenReturn(true);
        doNothing().when(transaction).begin();
        doNothing().when(transaction).commit();

        // Mock getAttachments query results
        List<Object[]> mockResults = Collections.singletonList(new Object[]{"123", "attachment1;attachment2"});
        Query selectQuery = mock(Query.class);
        Query deleteQuery = mock(Query.class);
        when(entityManager.createNativeQuery(any())).thenReturn(selectQuery).thenReturn(deleteQuery);
        when(selectQuery.getResultList()).thenReturn(mockResults);

        PageVersion mockPageVersion = mock(PageVersion.class);
        when(noteService.getPageVersionById(anyLong())).thenReturn(mockPageVersion);
        when(mockPageVersion.getParent()).thenReturn(null); // No parent to simplify this test

        // Run processUpgrade
        articleAttachmentsUpgradePlugin.processUpgrade("6.5.4", "7.0.0");

        // Verify behaviors
        verify(transaction, times(1)).begin();
        verify(attachmentStorage, times(2)).linkAttachmentToEntity(any(Long.class), any(String.class), any(String.class));
        verify(transaction, times(1)).commit();
    }

}
