/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.upgrade;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;

@RunWith(MockitoJUnitRunner.class)
public class EmailConnectorIdUpgradePluginTest {

  private EmailConnectorIdUpgradePlugin plugin;

  @Before
  public void setUp() throws InterruptedException {
    InitParams initParams = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.platform");
    initParams.addParameter(groupId);
    plugin = spy(new EmailConnectorIdUpgradePlugin(initParams));
    doNothing().when(plugin).awaitServerStartup();
  }

  @Test
  public void theDisconnectionIsDelegatedToTheServiceOnceTheServerIsUp() throws Exception {
    doReturn(0).when(plugin).disconnectUsersWithNonCanonicalConnectorId();

    plugin.processUpgrade("7.3.0", "7.3.1");

    InOrder inOrder = inOrder(plugin);
    inOrder.verify(plugin).awaitServerStartup();
    inOrder.verify(plugin).disconnectUsersWithNonCanonicalConnectorId();
  }

  @Test
  public void aRunThatLeavesAUserConnectedIsReportedSoItIsAttemptedAgain() throws Exception {
    doReturn(1).when(plugin).disconnectUsersWithNonCanonicalConnectorId();

    assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.3.0", "7.3.1"));
  }

  @Test
  public void aFailedSelectionIsReportedSoTheFrameworkDoesNotRecordTheRun() throws Exception {
    doThrow(new ExecutionException(new IllegalStateException("db down"))).when(plugin).disconnectUsersWithNonCanonicalConnectorId();

    assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.3.0", "7.3.1"));
  }
}
