package com.themis.mobile.ui;

import android.app.Application;
import com.themis.mobile.tracking.ActivityRepository;
import com.themis.mobile.tracking.TrackingStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class TrackingViewModel_Factory implements Factory<TrackingViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<ActivityRepository> repoProvider;

  private final Provider<TrackingStore> storeProvider;

  public TrackingViewModel_Factory(Provider<Application> appProvider,
      Provider<ActivityRepository> repoProvider, Provider<TrackingStore> storeProvider) {
    this.appProvider = appProvider;
    this.repoProvider = repoProvider;
    this.storeProvider = storeProvider;
  }

  @Override
  public TrackingViewModel get() {
    return newInstance(appProvider.get(), repoProvider.get(), storeProvider.get());
  }

  public static TrackingViewModel_Factory create(Provider<Application> appProvider,
      Provider<ActivityRepository> repoProvider, Provider<TrackingStore> storeProvider) {
    return new TrackingViewModel_Factory(appProvider, repoProvider, storeProvider);
  }

  public static TrackingViewModel newInstance(Application app, ActivityRepository repo,
      TrackingStore store) {
    return new TrackingViewModel(app, repo, store);
  }
}
