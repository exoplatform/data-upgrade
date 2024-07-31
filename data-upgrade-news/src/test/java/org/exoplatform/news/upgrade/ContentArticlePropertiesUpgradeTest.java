package org.exoplatform.news.upgrade;

import io.meeds.notes.model.NoteMetadataObject;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.social.metadata.model.MetadataItem;
import org.exoplatform.social.metadata.model.MetadataKey;
import org.exoplatform.wiki.model.DraftPage;
import org.exoplatform.wiki.model.Page;
import org.exoplatform.wiki.model.PageVersion;
import org.exoplatform.wiki.service.NoteService;
import org.exoplatform.wiki.utils.Utils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ContentArticlePropertiesUpgradeTest {

  @Mock
  private NoteService                     noteService;

  @Mock
  private MetadataService                 metadataService;

  @Mock
  private IdentityManager                 identityManager;

  @Mock
  private SpaceService                    spaceService;

  @Mock
  private UserACL                         userACL;

  private ContentArticlePropertiesUpgrade contentArticlePropertiesUpgrade;

  private static final MetadataKey        NOTES_METADATA_KEY = new MetadataKey("notes", Utils.NOTES_METADATA_OBJECT_TYPE, 0);

  private static final String             ILLUSTRATION_ID    = "illustrationId";

  private static final String             SUMMARY            = "summary";

  @Before
  public void setUp() {
    InitParams initParams = new InitParams();
    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);
    this.contentArticlePropertiesUpgrade = new ContentArticlePropertiesUpgrade(initParams,
                                                                               noteService,
                                                                               metadataService,
                                                                               identityManager,
                                                                               spaceService,
                                                                               userACL);
  }

  @Test
  public void processUpgrade() throws Exception {
    MetadataItem page = new MetadataItem();
    MetadataItem draftOfPage = new MetadataItem();
    MetadataItem draft = new MetadataItem();
    page.setId(1L);
    page.setObjectType("newsPageVersion");
    page.setObjectId("1");
    page.setProperties(Map.of(ILLUSTRATION_ID, "1", SUMMARY, "test summary"));

    draftOfPage.setId(2L);
    draftOfPage.setObjectType("newsLatestDraftPage");
    draftOfPage.setObjectId("2");
    draftOfPage.setProperties(Map.of(ILLUSTRATION_ID, "2", SUMMARY, "test summary"));

    draft.setId(1L);
    draft.setObjectId("3");
    draft.setObjectType("newsDraftPage");
    draft.setProperties(Map.of(ILLUSTRATION_ID, "3", SUMMARY, "test summary"));

    Page parentPage = new Page();
    parentPage.setId("2");
    parentPage.setAuthor("user");
    PageVersion pageVersion = new PageVersion();
    pageVersion.setId("1");
    pageVersion.setParent(parentPage);
    pageVersion.setAuthor("user");

    DraftPage draftOfExistingPage = new DraftPage();
    draftOfExistingPage.setId("2");
    draftOfExistingPage.setAuthor("user");

    DraftPage draftPage = new DraftPage();
    draftPage.setId("3");
    draftPage.setAuthor("user");

    MetadataItem notePage = new MetadataItem();
    notePage.setId(5L);
    notePage.setObjectType("notePage");
    notePage.setObjectId("2");

    List<MetadataItem> metadataItems = List.of(page, draftOfPage, draft);
    Identity identity = mock(Identity.class);
    when(metadataService.getMetadataItemsByFilter(any(), anyLong(), anyLong())).thenReturn(metadataItems);
    when(noteService.getPageVersionById(anyLong())).thenReturn(pageVersion);
    when(noteService.getDraftNoteById(anyString(), anyString())).thenReturn(draftOfExistingPage, draftPage);
    when(identityManager.getOrCreateUserIdentity(anyString())).thenReturn(identity);
    when(metadataService.getMetadataItemsByMetadataAndObject(NOTES_METADATA_KEY,
                                                             new NoteMetadataObject("noteDraftPage", "3", null, 0L)))
                                                                                                                     .thenReturn(new ArrayList<>());
    when(metadataService.getMetadataItemsByMetadataAndObject(NOTES_METADATA_KEY,
                                                             new NoteMetadataObject("noteDraftPage", "2", null, 0L)))
                                                                                                                     .thenReturn(new ArrayList<>());
    when(metadataService.getMetadataItemsByMetadataAndObject(NOTES_METADATA_KEY,
                                                             new NoteMetadataObject("notePage", "2", null, 0L)))
                                                                                                                .thenReturn(List.of(notePage));

    when(userACL.getSuperUser()).thenReturn("root");
    when(identity.getId()).thenReturn("1");
    contentArticlePropertiesUpgrade.processUpgrade(null, null);

    verify(metadataService, times(1)).updateMetadataItem(any(), anyLong());
    verify(metadataService, times(2)).createMetadataItem(any(), any(), any(), anyLong());
  }
}
