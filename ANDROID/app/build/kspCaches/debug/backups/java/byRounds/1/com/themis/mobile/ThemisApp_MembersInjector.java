package com.themis.mobile;

import com.themis.mobile.data.Repository;
import com.themis.mobile.data.ThemisDatabase;
import com.themis.mobile.sync.SyncEngine;
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
public final class ThemisApp_MembersInjector implements MembersInjector<ThemisApp> {
  private final Provider<ThemisDatabase> dbProvider;

  private final Provider<Repository> repoProvider;

  private final Provider<SyncEngine> syncEngineProvider;

  public ThemisApp_MembersInjector(Provider<ThemisDatabase> dbProvider,
      Provider<Repository> repoProvider, Provider<SyncEngine> syncEngineProvider) {
    this.dbProvider = dbProvider;
    this.repoProvider = repoProvider;
    this.syncEngineProvider = syncEngineProvider;
  }

  public static MembersInjector<ThemisApp> create(Provider<ThemisDatabase> dbProvider,
      Provider<Repository> repoProvider, Provider<SyncEngine> syncEngineProvider) {
    return new ThemisApp_MembersInjector(dbProvider, repoProvider, syncEngineProvider);
  }

  @Override
  public void injectMembers(ThemisApp instance) {
    injectDb(instance, dbProvider.get());
    injectRepo(instance, repoProvider.get());
    injectSyncEngine(instance, syncEngineProvider.get());
  }

  @InjectedFieldSignature("com.themis.mobile.ThemisApp.db")
  public static void injectDb(ThemisApp instance, ThemisDatabase db) {
    instance.db = db;
  }

  @InjectedFieldSignature("com.themis.mobile.ThemisApp.repo")
  public static void injectRepo(ThemisApp instance, Repository repo) {
    instance.repo = repo;
  }

  @InjectedFieldSignature("com.themis.mobile.ThemisApp.syncEngine")
  public static void injectSyncEngine(ThemisApp instance, SyncEngine syncEngine) {
    instance.syncEngine = syncEngine;
  }
}
