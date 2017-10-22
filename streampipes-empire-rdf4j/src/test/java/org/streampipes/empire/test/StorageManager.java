package org.streampipes.empire.test;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.inferencer.fc.SchemaCachingRDFSInferencer;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.streampipes.empire.core.empire.Empire;
import org.streampipes.empire.core.empire.EmpireOptions;
import org.streampipes.empire.core.empire.config.ConfigKeys;
import org.streampipes.empire.core.empire.config.EmpireConfiguration;
import org.streampipes.empire.rdf4j.OpenRdfEmpireModule;
import org.streampipes.empire.rdf4j.RepositoryFactoryKeys;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.spi.PersistenceProvider;

public enum StorageManager {

  INSTANCE;

  private EntityManager storageManager;

  //private RepositoryConnection conn;

  private Repository repository;

  private boolean inMemoryInitialized = false;

  String sesameUrl = "http://localhost:8030/rdf4j-server";
  String sesameDbName = "streampipes-inmemory-delay-rdfs-sc2";

  StorageManager() {
    initSesameDatabases();
  }

  public void initSesameDatabases() {
    initStorage();
    initEmpire();
  }


  private boolean initStorage() {
    try {

      RemoteRepositoryManager manager = new RemoteRepositoryManager(sesameUrl);
      manager.initialize();
      //RepositoryConfig config = new RepositoryConfig(sesameDbName, "StreamPipes DB");
      //SailImplConfig backendConfig = new MemoryStoreConfig(true, 10000);
      //backendConfig = new ForwardChainingRDFSInferencerConfig(backendConfig);
      //config.setRepositoryImplConfig(new SailRepositoryConfig(backendConfig));
      //manager.addRepositoryConfig(config);

//            repository = new HTTPRepository(sesameUrl,
//                    sesameDbName);
//            repository.initialize();


      // performance is magnitudes faster
      MemoryStore memoryStore = new MemoryStore();
      memoryStore.setPersist(true);
      memoryStore.setSyncDelay(1000);
      repository = new SailRepository(new SchemaCachingRDFSInferencer(memoryStore));
      repository.initialize();
      //conn = repository.getConnection();

      initEmpire();

      return true;
    } catch (
            Exception e)

    {
      e.printStackTrace();
      return false;
    }

  }

  private boolean initEmpire() {

    try {
      EmpireOptions.STRICT_MODE = false;
      EmpireConfiguration empireCfg = new EmpireConfiguration();
      //empireCfg.setAnnotationProvider(CustomAnnotationProvider.class);

      Empire.init(empireCfg, new OpenRdfEmpireModule());
      Map<Object, Object> map = new HashMap<Object, Object>();

      map.put(RepositoryFactoryKeys.REPO_HANDLE, repository);
      map.put(ConfigKeys.FACTORY, "sesame");
      map.put(ConfigKeys.NAME, "sepa-server");
      map.put("url", "http://localhost:8030/rdf4j-server");
      map.put("repo", sesameDbName);

      PersistenceProvider provider = Empire.get().persistenceProvider();
      storageManager = provider.createEntityManagerFactory("sepa-server", map).createEntityManager();

      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

  }

   /* public RepositoryConnection getConnection() {
        return conn;
    }*/

//    public StorageRequests getStorageAPI() {
//        if (backgroundKnowledgeStorage == null) {
//            initSesameDatabases();
//        }
//        if (!inMemoryInitialized) {
//            this.inMemoryStorage = new InMemoryStorage(getSesameStorage());
//            inMemoryInitialized = true;
//        }
//        return this.inMemoryStorage;
//
//    }

  public EntityManager getEntityManager() {
    return storageManager;
  }

  public StorageRequests getSesameStorage() {
    return new SesameStorageRequests();
  }


}
