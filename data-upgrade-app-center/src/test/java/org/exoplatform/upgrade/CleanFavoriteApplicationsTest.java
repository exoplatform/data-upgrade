package org.exoplatform.upgrade;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.container.xml.InitParams;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class CleanFavoriteApplicationsTest {

  @Mock
  private EntityManagerService      entityManagerService;

  @Mock
  private EntityManager             entityManager;

  @Mock
  private Query                     query;

  private CleanFavoriteApplications cleanFavoriteApplications;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    InitParams initParams = new InitParams();
    cleanFavoriteApplications = new CleanFavoriteApplications(initParams, entityManagerService);
    when(entityManagerService.getEntityManager()).thenReturn(entityManager);
  }

  @Test
  public void processUpgradeCleanDuplicatedFavoriteApps() {
    List<Object[]> results = List.of(new Object[] { 1L, 100L, "user1" },
                                     new Object[] { 2L, 100L, "user1" },
                                     new Object[] { 3L, 101L, "test" },
                                     new Object[] { 4L, 101L, "test" });

    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.getResultList()).thenReturn(results);
    when(query.executeUpdate()).thenReturn(2);

    cleanFavoriteApplications.processUpgrade("v1", "v1");

    verify(entityManager, times(3)).createNativeQuery(anyString());
    verify(query, times(2)).executeUpdate();
    verify(entityManagerService, times(1)).getEntityManager();
  }

  @Test
  public void processUpgradeNoDuplicatedFavoriteApps() {
    List<Object[]> results = List.of(new Object[] { 1L, 100L, "user1" }, new Object[] { 2L, 101L, "test" });

    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.getResultList()).thenReturn(results);

    cleanFavoriteApplications.processUpgrade("v1", "v1");

    verify(query, never()).executeUpdate();
  }
}
