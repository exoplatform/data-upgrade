package org.exoplatform.migration.dlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.junit.Test;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.Workspace;

@RunWith(PowerMockRunner.class)
public class DlpFolderAndDriveMigrationTest {

  @Mock
  RepositoryService  repositoryService;

  @Mock
  ManageDriveService manageDriveService;

  @Mock
  SessionProviderService       sessionProviderService;

  @Mock
  ManageableRepository         repository;

  @Mock
  SessionProvider              sessionProvider;

  @Mock
  Session                      session;

  @Mock
  NodeIterator                 nodeIterator;

  @Mock
  SettingService               settingService;

  @Test
  public void testDlpFolderAndDriveMigration() throws Exception {

    InitParams initParams = new InitParams();

    ValueParam newNode = new ValueParam();
    newNode.setName("new.nodePath");
    newNode.setValue("/Quarantine");
    ValueParam oldNode = new ValueParam();
    oldNode.setName("old.nodePath");
    oldNode.setValue("/Security");
    initParams.addParameter(newNode);
    initParams.addParameter(oldNode);
    DriveData driveQuarantine = mock(DriveData.class);
    DriveData driveSecurity = mock(DriveData.class);

    Workspace workspace = mock(Workspace.class);
    when(sessionProviderService.getSystemSessionProvider(any())).thenReturn(sessionProvider);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(sessionProvider.getSession(any(), any())).thenReturn(session);
    when(session.getWorkspace()).thenReturn(workspace);
    when(session.itemExists(any())).thenReturn(true);
    Node securityNode = mock(Node.class);
    when((Node) session.getItem("/Security")).thenReturn(securityNode);
    when(securityNode.hasNodes()).thenReturn(true);
    when(securityNode.getNodes()).thenReturn(nodeIterator);
    when(nodeIterator.hasNext()).thenReturn(true, true, false);
    Node childSecurityNode = mock(Node.class);
    when(nodeIterator.nextNode()).thenReturn(childSecurityNode);

    when(manageDriveService.getDriveByName("Quarantine")).thenReturn(driveQuarantine);
    when(manageDriveService.getDriveByName("Security")).thenReturn(driveSecurity);

    assertNotNull(manageDriveService.getDriveByName("Quarantine"));

    DlpFolderAndDriveMigration dlpFolderAndDriveMigration = new DlpFolderAndDriveMigration(initParams,
                                                                                           settingService,
                                                                                           repositoryService,
                                                                                           manageDriveService,
                                                                                           sessionProviderService);
    dlpFolderAndDriveMigration.processUpgrade(null, null);
    assertEquals(2, dlpFolderAndDriveMigration.getNodesMovedCount());

  }
}
