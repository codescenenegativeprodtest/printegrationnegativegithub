/**
 * Copyright (C) 2010 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.persist.jpa;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.Nullable;
import com.google.inject.internal.util.Preconditions;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.WorkManager;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class EntityManagerProvider implements Provider<EntityManager>, WorkManager {
  private final ThreadLocal<EntityManager> entityManager = new ThreadLocal<EntityManager>();

  private final String persistenceUnitName;
  private final Properties persistenceProperties;

  @Inject
  public EntityManagerProvider(@PersistModule.Persist String persistenceUnitName,
      @Nullable @PersistModule.Persist Properties persistenceProperties) {
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceProperties = persistenceProperties;
  }

  public EntityManager get() {
    if (!isWorking()) {
      begin();
    }

    EntityManager em = entityManager.get();
    Preconditions.checkState(null != em, "Requested EntityManager outside work unit. "
        + "Try calling WorkManager.begin() first, or use a PersistenceFilter if you "
        + "are inside a servlet environment.");

    return em;
  }

  public boolean isWorking() {
    return entityManager.get() != null;
  }

  public void begin() {
    Preconditions.checkState(null == entityManager.get(),
        "Work already begun on this thread. Looks like you have called WorkManager.begin() twice"
         + " without a balancing call to end() in between.");

    entityManager.set(emFactory.createEntityManager());
  }

  public void end() {
    EntityManager em = entityManager.get();

    // Let's not penalize users for calling end() multiple times.
    if (null == em) {
      return;
    }

    em.close();
    entityManager.remove();
  }

  private volatile EntityManagerFactory emFactory;

  public synchronized void startPersistence() {
    Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");

    if (null != persistenceProperties) {
      this.emFactory = Persistence
          .createEntityManagerFactory(persistenceUnitName, persistenceProperties);
    } else {
      this.emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
    }
  }

  public synchronized void shutdownPersistence() {
    Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
    emFactory.close();
  }

  @Singleton
  public static class EntityManagerFactoryProvider implements Provider<EntityManagerFactory> {
    private final EntityManagerProvider emProvider;

    @Inject
    public EntityManagerFactoryProvider(EntityManagerProvider emProvider) {
      this.emProvider = emProvider;
    }

    public EntityManagerFactory get() {
      assert null != emProvider.emFactory;
      return emProvider.emFactory;
    }
  }
}