package com.themis.mobile.di;

import android.content.Context;
import com.themis.mobile.data.ThemisDatabase;
import com.themis.mobile.sync.SyncEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_SyncEngineFactory implements Factory<SyncEngine> {
  private final Provider<Context> cProvider;

  private final Provider<ThemisDatabase> dbProvider;

  public AppModule_SyncEngineFactory(Provider<Context> cProvider,
      Provider<ThemisDatabase> dbProvider) {
    this.cProvider = cProvider;
    this.dbProvider = dbProvider;
  }

  @Override
  public SyncEngine get() {
    return syncEngine(cProvider.get(), dbProvider.get());
  }

  public static AppModule_SyncEngineFactory create(Provider<Context> cProvider,
      Provider<ThemisDatabase> dbProvider) {
    return new AppModule_SyncEngineFactory(cProvider, dbProvider);
  }

  public static SyncEngine syncEngine(Context c, ThemisDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.syncEngine(c, db));
  }
}
