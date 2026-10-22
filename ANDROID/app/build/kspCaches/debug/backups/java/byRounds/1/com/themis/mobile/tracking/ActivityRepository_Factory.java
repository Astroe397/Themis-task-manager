package com.themis.mobile.tracking;

import com.themis.mobile.data.ThemisDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ActivityRepository_Factory implements Factory<ActivityRepository> {
  private final Provider<ThemisDatabase> dbProvider;

  public ActivityRepository_Factory(Provider<ThemisDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ActivityRepository get() {
    return newInstance(dbProvider.get());
  }

  public static ActivityRepository_Factory create(Provider<ThemisDatabase> dbProvider) {
    return new ActivityRepository_Factory(dbProvider);
  }

  public static ActivityRepository newInstance(ThemisDatabase db) {
    return new ActivityRepository(db);
  }
}
