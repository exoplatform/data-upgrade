package org.exoplatform.portal;

import org.exoplatform.commons.chromattic.ChromatticManager;
import org.exoplatform.commons.chromattic.Synchronization;
import org.exoplatform.component.test.*;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.jdbc.migration.MigrationContext;

@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.identity-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.portal-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.application-registry-configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/test.mop.portal.configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/exo.portal.component.test.chromattic-impl-configuration.xml"),
})
public abstract class AbstractJCRImplTest extends AbstractKernelTest {

  public AbstractJCRImplTest() {
  }

  public AbstractJCRImplTest(String name) {
    super(name);
  }

  @Override
  protected void end() {
    end(false);
  }

  protected void end(boolean save) {
    PortalContainer container = getContainer();
    ChromatticManager manager = container.getComponentInstanceOfType(ChromatticManager.class);
    Synchronization synchronization = manager.getSynchronization();
    if (synchronization != null) {
      synchronization.setSaveOnClose(save);
    }
    super.end();
  }

  protected final void sync() {
    restartTransaction(false);
  }

  protected final void sync(boolean save) {
    restartTransaction(save);
  }

  @Override
  protected void setUp() throws Exception {
    begin();
  }

  @Override
  protected void tearDown() throws Exception {
    end();
  }

  protected <T> T getService(Class<T> clazz) {
    return getContainer().getComponentInstanceOfType(clazz);
  }

  protected void restartTransaction(boolean save) {
    int i = 0;
    // Close transactions until no encapsulated transaction
    boolean success = true;
    do {
      try {
        end(save);
        i++;
      } catch (IllegalStateException e) {
        success = false;
      }
    } while (success);

    // Restart transactions with the same number of encapsulations
    for (int j = 0; j < i; j++) {
      begin();
    }
  }
}
