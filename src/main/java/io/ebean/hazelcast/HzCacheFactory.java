package io.ebean.hazelcast;

import io.ebean.BackgroundExecutor;
import io.ebean.cache.ServerCache;
import io.ebean.cache.ServerCacheFactory;
import io.ebean.cache.ServerCacheOptions;
import io.ebean.cache.ServerCacheType;
import io.ebean.config.ServerConfig;
import io.ebeaninternal.server.cache.DefaultServerCache;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating the various caches.
 */
public class HzCacheFactory implements ServerCacheFactory {

  /**
   * This explicitly uses the common "org.avaje.ebean.cache" namespace.
   */
  private static final Logger logger = LoggerFactory.getLogger("org.avaje.ebean.cache.HzCacheFactory");

  private final ConcurrentHashMap<String,HzQueryCache> queryCaches;

  private final HazelcastInstance instance;

  /**
   * Topic used to broadcast query cache invalidation.
   */
  private final ITopic<String> queryCacheInvalidation;

  private final BackgroundExecutor executor;

  public HzCacheFactory(ServerConfig serverConfig, BackgroundExecutor executor) {

    this.executor = executor;
    this.queryCaches = new ConcurrentHashMap<String, HzQueryCache>();

    if (System.getProperty("hazelcast.logging.type") == null) {
      System.setProperty("hazelcast.logging.type", "slf4j");
    }

    Object hazelcastInstance = serverConfig.getServiceObject("hazelcast");
    if (hazelcastInstance != null) {
      instance = (HazelcastInstance)hazelcastInstance;
    } else {
      instance = createInstance(serverConfig);
    }

    queryCacheInvalidation = instance.getReliableTopic("queryCacheInvalidation");
    queryCacheInvalidation.addMessageListener(new MessageListener<String>() {
      @Override
      public void onMessage(Message<String> message) {
        processInvalidation(message.getMessageObject());
      }
    });
  }

  /**
   * Create a new HazelcastInstance based on configuration from serverConfig.
   */
  private HazelcastInstance createInstance(ServerConfig serverConfig) {
    Object configuration = serverConfig.getServiceObject("hazelcastConfiguration");
    if (configuration != null) {
      // explicit configuration probably set via DI
      if (configuration instanceof ClientConfig) {
        return HazelcastClient.newHazelcastClient((ClientConfig) configuration);
      } else if (configuration instanceof Config) {
        return Hazelcast.newHazelcastInstance((Config) configuration);
      } else {
        throw new IllegalArgumentException("Invalid Hazelcast configuration type " + configuration.getClass());
      }
    } else {
      // implicit configuration via hazelcast-client.xml or hazelcast.xml
      if (isServerMode(serverConfig.getProperties())) {
        return Hazelcast.newHazelcastInstance();
      } else {
        return HazelcastClient.newHazelcastClient();
      }
    }
  }

  /**
   * Return true if hazelcast should be used in server mode.
   */
  private boolean isServerMode(Properties properties) {
    return properties != null && properties.getProperty("ebean.hazelcast.servermode","").equals("true");
  }

  @Override
  public ServerCache createCache(ServerCacheType type, String key, ServerCacheOptions options) {

    switch (type) {
      case QUERY:
        return createQueryCache(key, options);
      default:
        return createNormalCache(type, key, options);
    }
  }

  private ServerCache createNormalCache(ServerCacheType type, String key, ServerCacheOptions options) {

    String fullName = type.name() + "-" + key;
    logger.debug("get cache [{}]", fullName);
    IMap<Object, Object> map = instance.getMap(fullName);
    return new HzCache(map);
  }

  private ServerCache createQueryCache(String key, ServerCacheOptions options) {

    synchronized (this) {
      HzQueryCache cache = queryCaches.get(key);
      if (cache == null) {
        logger.debug("create query cache [{}]", key);
        cache = new HzQueryCache(key, options);
        cache.periodicTrim(executor);
        queryCaches.put(key, cache);
      }
      return cache;
    }
  }

  /**
   * Extends normal default implementation with notification of clear() to cluster.
   */
  private class HzQueryCache extends DefaultServerCache {

    HzQueryCache(String name, ServerCacheOptions options) {
      super(name, options);
    }

    @Override
    public void clear() {
      super.clear();
      sendInvalidation(name);
    }

    /**
     * Process the invalidation message coming from the cluster.
     */
    private void invalidate() {
      super.clear();
    }
  }

  /**
   * Send the invalidation message to all members of the cluster.
   */
  private void sendInvalidation(String key) {
    queryCacheInvalidation.publish(key);
  }

  /**
   * Process a remote query cache invalidation.
   */
  private void processInvalidation(String cacheName) {
    HzQueryCache cache = queryCaches.get(cacheName);
    if (cache != null) {
      cache.invalidate();
    }
  }

}