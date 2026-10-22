package com.themis.mobile.di;

import android.content.Context;
import com.themis.mobile.data.ThemisDatabase;
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
public final class AppModule_DatabaseFactory implements Factory<ThemisDatabase> {
  private final Provider<Context> cProvider;

  public AppModule_DatabaseFactory(Provider<Context> cProvider) {
    this.cProvider = cProvider;
  }

  @Override
  public ThemisDatabase get() {
    return database(cProvider.get());
  }

  public static AppModule_DatabaseFactory create(Provider<Context> cProvider) {
    return new AppModule_DatabaseFactory(cProvider);
  }

  public static ThemisDatabase database(Context c) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.database(c));
  }
}
