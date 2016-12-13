package io.ebean.hazelcast;

import io.ebean.cache.ServerCache;
import io.ebean.cache.ServerCacheStatistics;
import com.hazelcast.core.IMap;

/**
 * IMap cache implementation for Ebean's ServerCache interface.
 */
class HzCache implements ServerCache {

  private final IMap<Object, Object> map;

  HzCache(IMap<Object, Object> map) {
    this.map = map;
  }

  @Override
  public Object get(Object id) {
    return map.get(id);
  }

  @Override
  public Object put(Object id, Object value) {
    return map.put(id, value);
  }

  @Override
  public Object remove(Object id) {
    return map.remove(id);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public int getHitRatio() {
    return 0;
  }

  @Override
  public ServerCacheStatistics getStatistics(boolean reset) {
    //LocalMapStats localMapStats = map.getLocalMapStats();
    return null;
  }

}