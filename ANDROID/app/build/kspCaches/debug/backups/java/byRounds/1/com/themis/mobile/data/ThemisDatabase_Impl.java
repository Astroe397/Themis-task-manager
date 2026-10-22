package com.themis.mobile.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ThemisDatabase_Impl extends ThemisDatabase {
  private volatile TaskDao _taskDao;

  private volatile CompletionDao _completionDao;

  private volatile WeightDao _weightDao;

  private volatile SettingDao _settingDao;

  private volatile ActivityDao _activityDao;

  private volatile TrackPointDao _trackPointDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(3) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, `isOneOff` INTEGER NOT NULL DEFAULT 0, `isDone` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tasks_uid` ON `tasks` (`uid`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `completions` (`taskId` INTEGER NOT NULL, `day` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`taskId`, `day`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`day` TEXT NOT NULL, `kg` REAL NOT NULL, `updatedAt` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`day`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `status` TEXT NOT NULL, `distanceMeters` REAL NOT NULL, `elapsedMs` INTEGER NOT NULL, `movingTimeMs` INTEGER NOT NULL, `steps` INTEGER NOT NULL, `baselineSteps` INTEGER NOT NULL, `stepOffset` INTEGER NOT NULL, `lastHardwareSteps` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `activityId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL NOT NULL, `speed` REAL NOT NULL, `accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_activityId` ON `track_points` (`activityId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5b6a53daf9283ddc0ce06f18aac379a1')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `tasks`");
        db.execSQL("DROP TABLE IF EXISTS `completions`");
        db.execSQL("DROP TABLE IF EXISTS `weights`");
        db.execSQL("DROP TABLE IF EXISTS `settings`");
        db.execSQL("DROP TABLE IF EXISTS `activities`");
        db.execSQL("DROP TABLE IF EXISTS `track_points`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTasks = new HashMap<String, TableInfo.Column>(9);
        _columnsTasks.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("uid", new TableInfo.Column("uid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("createdAt", new TableInfo.Column("createdAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("updatedAt", new TableInfo.Column("updatedAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("isOneOff", new TableInfo.Column("isOneOff", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("isDone", new TableInfo.Column("isDone", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTasks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTasks = new HashSet<TableInfo.Index>(1);
        _indicesTasks.add(new TableInfo.Index("index_tasks_uid", true, Arrays.asList("uid"), Arrays.asList("ASC")));
        final TableInfo _infoTasks = new TableInfo("tasks", _columnsTasks, _foreignKeysTasks, _indicesTasks);
        final TableInfo _existingTasks = TableInfo.read(db, "tasks");
        if (!_infoTasks.equals(_existingTasks)) {
          return new RoomOpenHelper.ValidationResult(false, "tasks(com.themis.mobile.data.TaskEntity).\n"
                  + " Expected:\n" + _infoTasks + "\n"
                  + " Found:\n" + _existingTasks);
        }
        final HashMap<String, TableInfo.Column> _columnsCompletions = new HashMap<String, TableInfo.Column>(4);
        _columnsCompletions.put("taskId", new TableInfo.Column("taskId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCompletions.put("day", new TableInfo.Column("day", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCompletions.put("updatedAt", new TableInfo.Column("updatedAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCompletions.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCompletions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCompletions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCompletions = new TableInfo("completions", _columnsCompletions, _foreignKeysCompletions, _indicesCompletions);
        final TableInfo _existingCompletions = TableInfo.read(db, "completions");
        if (!_infoCompletions.equals(_existingCompletions)) {
          return new RoomOpenHelper.ValidationResult(false, "completions(com.themis.mobile.data.CompletionEntity).\n"
                  + " Expected:\n" + _infoCompletions + "\n"
                  + " Found:\n" + _existingCompletions);
        }
        final HashMap<String, TableInfo.Column> _columnsWeights = new HashMap<String, TableInfo.Column>(4);
        _columnsWeights.put("day", new TableInfo.Column("day", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeights.put("kg", new TableInfo.Column("kg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeights.put("updatedAt", new TableInfo.Column("updatedAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeights.put("isDeleted", new TableInfo.Column("isDeleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWeights = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWeights = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWeights = new TableInfo("weights", _columnsWeights, _foreignKeysWeights, _indicesWeights);
        final TableInfo _existingWeights = TableInfo.read(db, "weights");
        if (!_infoWeights.equals(_existingWeights)) {
          return new RoomOpenHelper.ValidationResult(false, "weights(com.themis.mobile.data.WeightEntity).\n"
                  + " Expected:\n" + _infoWeights + "\n"
                  + " Found:\n" + _existingWeights);
        }
        final HashMap<String, TableInfo.Column> _columnsSettings = new HashMap<String, TableInfo.Column>(3);
        _columnsSettings.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("value", new TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("updatedAt", new TableInfo.Column("updatedAt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSettings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSettings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSettings = new TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings);
        final TableInfo _existingSettings = TableInfo.read(db, "settings");
        if (!_infoSettings.equals(_existingSettings)) {
          return new RoomOpenHelper.ValidationResult(false, "settings(com.themis.mobile.data.SettingEntity).\n"
                  + " Expected:\n" + _infoSettings + "\n"
                  + " Found:\n" + _existingSettings);
        }
        final HashMap<String, TableInfo.Column> _columnsActivities = new HashMap<String, TableInfo.Column>(11);
        _columnsActivities.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("startedAt", new TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("endedAt", new TableInfo.Column("endedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("distanceMeters", new TableInfo.Column("distanceMeters", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("elapsedMs", new TableInfo.Column("elapsedMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("movingTimeMs", new TableInfo.Column("movingTimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("steps", new TableInfo.Column("steps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("baselineSteps", new TableInfo.Column("baselineSteps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("stepOffset", new TableInfo.Column("stepOffset", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsActivities.put("lastHardwareSteps", new TableInfo.Column("lastHardwareSteps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysActivities = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesActivities = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoActivities = new TableInfo("activities", _columnsActivities, _foreignKeysActivities, _indicesActivities);
        final TableInfo _existingActivities = TableInfo.read(db, "activities");
        if (!_infoActivities.equals(_existingActivities)) {
          return new RoomOpenHelper.ValidationResult(false, "activities(com.themis.mobile.data.ActivityEntity).\n"
                  + " Expected:\n" + _infoActivities + "\n"
                  + " Found:\n" + _existingActivities);
        }
        final HashMap<String, TableInfo.Column> _columnsTrackPoints = new HashMap<String, TableInfo.Column>(8);
        _columnsTrackPoints.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("activityId", new TableInfo.Column("activityId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("latitude", new TableInfo.Column("latitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("longitude", new TableInfo.Column("longitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("altitude", new TableInfo.Column("altitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("speed", new TableInfo.Column("speed", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("accuracy", new TableInfo.Column("accuracy", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrackPoints.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrackPoints = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysTrackPoints.add(new TableInfo.ForeignKey("activities", "CASCADE", "NO ACTION", Arrays.asList("activityId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesTrackPoints = new HashSet<TableInfo.Index>(1);
        _indicesTrackPoints.add(new TableInfo.Index("index_track_points_activityId", false, Arrays.asList("activityId"), Arrays.asList("ASC")));
        final TableInfo _infoTrackPoints = new TableInfo("track_points", _columnsTrackPoints, _foreignKeysTrackPoints, _indicesTrackPoints);
        final TableInfo _existingTrackPoints = TableInfo.read(db, "track_points");
        if (!_infoTrackPoints.equals(_existingTrackPoints)) {
          return new RoomOpenHelper.ValidationResult(false, "track_points(com.themis.mobile.data.TrackPointEntity).\n"
                  + " Expected:\n" + _infoTrackPoints + "\n"
                  + " Found:\n" + _existingTrackPoints);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "5b6a53daf9283ddc0ce06f18aac379a1", "6351bad44206357fc3b0882b2cc538f3");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "tasks","completions","weights","settings","activities","track_points");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `tasks`");
      _db.execSQL("DELETE FROM `completions`");
      _db.execSQL("DELETE FROM `weights`");
      _db.execSQL("DELETE FROM `settings`");
      _db.execSQL("DELETE FROM `activities`");
      _db.execSQL("DELETE FROM `track_points`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TaskDao.class, TaskDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CompletionDao.class, CompletionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WeightDao.class, WeightDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingDao.class, SettingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ActivityDao.class, ActivityDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TrackPointDao.class, TrackPointDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TaskDao taskDao() {
    if (_taskDao != null) {
      return _taskDao;
    } else {
      synchronized(this) {
        if(_taskDao == null) {
          _taskDao = new TaskDao_Impl(this);
        }
        return _taskDao;
      }
    }
  }

  @Override
  public CompletionDao completionDao() {
    if (_completionDao != null) {
      return _completionDao;
    } else {
      synchronized(this) {
        if(_completionDao == null) {
          _completionDao = new CompletionDao_Impl(this);
        }
        return _completionDao;
      }
    }
  }

  @Override
  public WeightDao weightDao() {
    if (_weightDao != null) {
      return _weightDao;
    } else {
      synchronized(this) {
        if(_weightDao == null) {
          _weightDao = new WeightDao_Impl(this);
        }
        return _weightDao;
      }
    }
  }

  @Override
  public SettingDao settingDao() {
    if (_settingDao != null) {
      return _settingDao;
    } else {
      synchronized(this) {
        if(_settingDao == null) {
          _settingDao = new SettingDao_Impl(this);
        }
        return _settingDao;
      }
    }
  }

  @Override
  public ActivityDao activityDao() {
    if (_activityDao != null) {
      return _activityDao;
    } else {
      synchronized(this) {
        if(_activityDao == null) {
          _activityDao = new ActivityDao_Impl(this);
        }
        return _activityDao;
      }
    }
  }

  @Override
  public TrackPointDao trackPointDao() {
    if (_trackPointDao != null) {
      return _trackPointDao;
    } else {
      synchronized(this) {
        if(_trackPointDao == null) {
          _trackPointDao = new TrackPointDao_Impl(this);
        }
        return _trackPointDao;
      }
    }
  }
}
