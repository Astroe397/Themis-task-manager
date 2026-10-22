package com.themis.mobile.tracking;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class TrackingStore_Factory implements Factory<TrackingStore> {
  @Override
  public TrackingStore get() {
    return newInstance();
  }

  public static TrackingStore_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TrackingStore newInstance() {
    return new TrackingStore();
  }

  private static final class InstanceHolder {
    private static final TrackingStore_Factory INSTANCE = new TrackingStore_Factory();
  }
}
