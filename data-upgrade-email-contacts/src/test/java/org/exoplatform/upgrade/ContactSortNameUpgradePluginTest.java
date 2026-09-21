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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.exoplatform.commons.upgrade.UpgradePluginException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.emailConnector.service.EmailContactService;

@RunWith(MockitoJUnitRunner.class)
public class ContactSortNameUpgradePluginTest {

  @Mock
  private EmailContactService          emailContactService;

  private ContactSortNameUpgradePlugin plugin;

  @Before
  public void setUp() throws InterruptedException {
    InitParams initParams = new InitParams();
    ValueParam groupId = new ValueParam();
    groupId.setName("product.group.id");
    groupId.setValue("org.exoplatform.platform");
    initParams.addParameter(groupId);
    plugin = spy(new ContactSortNameUpgradePlugin(initParams));
    doNothing().when(plugin).awaitServerStartup();
    doReturn(emailContactService).when(plugin).contactService();
  }

  @Test
  public void theRecomputationIsDelegatedToTheContactServiceOnceTheServerIsUp() throws InterruptedException {
    when(emailContactService.recomputeContactSortNames()).thenReturn(3);

    plugin.processUpgrade("7.3.0", "7.3.1");

    verify(plugin).awaitServerStartup();
    verify(emailContactService).recomputeContactSortNames();
  }

  @Test
  public void aFailedRecomputationIsReportedSoTheFrameworkDoesNotRecordTheRun() {
    when(emailContactService.recomputeContactSortNames()).thenThrow(new IllegalStateException("db down"));

    assertThrows(UpgradePluginException.class, () -> plugin.processUpgrade("7.3.0", "7.3.1"));
  }
}
