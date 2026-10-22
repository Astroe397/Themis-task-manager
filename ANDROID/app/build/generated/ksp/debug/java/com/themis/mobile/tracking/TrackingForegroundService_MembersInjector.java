package com.themis.mobile.tracking;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class TrackingForegroundService_MembersInjector implements MembersInjector<TrackingForegroundService> {
  private final Provider<ActivityRepository> repoProvider;

  private final Provider<TrackingStore> storeProvider;

  public TrackingForegroundService_MembersInjector(Provider<ActivityRepository> repoProvider,
      Provider<TrackingStore> storeProvider) {
    this.repoProvider = repoProvider;
    this.storeProvider = storeProvider;
  }

  public static MembersInjector<TrackingForegroundService> create(
      Provider<ActivityRepository> repoProvider, Provider<TrackingStore> storeProvider) {
    return new TrackingForegroundService_MembersInjector(repoProvider, storeProvider);
  }

  @Override
  public void injectMembers(TrackingForegroundService instance) {
    injectRepo(instance, repoProvider.get());
    injectStore(instance, storeProvider.get());
  }

  @InjectedFieldSignature("com.themis.mobile.tracking.TrackingForegroundService.repo")
  public static void injectRepo(TrackingForegroundService instance, ActivityRepository repo) {
    instance.repo = repo;
  }

  @InjectedFieldSignature("com.themis.mobile.tracking.TrackingForegroundService.store")
  public static void injectStore(TrackingForegroundService instance, TrackingStore store) {
    instance.store = store;
  }
}
