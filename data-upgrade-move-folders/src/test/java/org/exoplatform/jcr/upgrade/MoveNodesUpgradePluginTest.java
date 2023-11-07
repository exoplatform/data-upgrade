package org.exoplatform.jcr.upgrade;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.social.core.space.SpaceListAccess;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jcr.Item;
import javax.jcr.Session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MoveNodesUpgradePluginTest {
  @Mock
  private RepositoryService repositoryService;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  private ManageableRepository repository;

  @Mock
  private RepositoryEntry repositoryEntry;

  @Mock
  private SessionProvider sessionProvider;

  @Mock
  private Session session;

  @Mock
  private SpaceService spaceService;

  @Before
  public void setUp() throws Exception {
    lenient().when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    lenient().when(repositoryService.getCurrentRepository()).thenReturn(repository);
    lenient().when(repository.getConfiguration()).thenReturn(repositoryEntry);
    lenient().when(repositoryEntry.getDefaultWorkspaceName()).thenReturn("collaboration");
    Item node = mock(Item.class);
    when(session.getItem(anyString())).thenReturn(node);
    lenient().when(sessionProvider.getSession(any(), any(ManageableRepository.class))).thenReturn(session);
  }

  @Test
  public void testMoveFoldersUpgrade() throws Exception {
    InitParams initParams = new InitParams();

    ValueParam valueParam = new ValueParam();
    valueParam.setName("product.group.id");
    valueParam.setValue("org.exoplatform.platform");
    initParams.addParameter(valueParam);

    MoveNodesUpgradePlugin plugin = new MoveNodesUpgradePlugin(initParams,
            spaceService,
            repositoryService,
            sessionProviderService);
    plugin.processUpgrade(null,null);

    verify(session, never()).move(anyString(), anyString());


    ValueParam valueParam1 = new ValueParam();
    valueParam1.setName("origin-folder-path");
    valueParam1.setValue("/Documents/News");
    initParams.addParameter(valueParam1);
    ValueParam valueParam2 = new ValueParam();
    valueParam2.setName("destination-folder-path");
    valueParam2.setValue("/");
    initParams.addParameter(valueParam2);
    Space space1 = new Space();
    space1.setGroupId("/spaces/spaceOne");

    Space space2 = new Space();
    space2.setGroupId("/spaces/spaceTw");

    ListAccess<Space> spaces = mock(SpaceListAccess.class);
    when(spaces.load(anyInt(), anyInt())).thenReturn(new Space[]{space1, space2});

    when(spaceService.getAllSpacesWithListAccess()).thenReturn(spaces);

    plugin = new MoveNodesUpgradePlugin(initParams,
            spaceService,
            repositoryService,
            sessionProviderService);
    plugin.processUpgrade(null,null);

    verify(session, times(2)).move(anyString(), anyString());
  }
}
