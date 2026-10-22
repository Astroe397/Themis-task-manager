package com.themis.mobile.di;

import com.themis.mobile.data.Repository;
import com.themis.mobile.data.ThemisDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_RepositoryFactory implements Factory<Repository> {
  private final Provider<ThemisDatabase> dbProvider;

  public AppModule_RepositoryFactory(Provider<ThemisDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public Repository get() {
    return repository(dbProvider.get());
  }

  public static AppModule_RepositoryFactory create(Provider<ThemisDatabase> dbProvider) {
    return new AppModule_RepositoryFactory(dbProvider);
  }

  public static Repository repository(ThemisDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.repository(db));
  }
}
