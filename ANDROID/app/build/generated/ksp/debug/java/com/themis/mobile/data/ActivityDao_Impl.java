package com.themis.mobile.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ActivityDao_Impl implements ActivityDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ActivityEntity> __insertionAdapterOfActivityEntity;

  private final SharedSQLiteStatement __preparedStmtOfSaveProgress;

  private final SharedSQLiteStatement __preparedStmtOfSetStatus;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public ActivityDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfActivityEntity = new EntityInsertionAdapter<ActivityEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `activities` (`id`,`startedAt`,`endedAt`,`status`,`distanceMeters`,`elapsedMs`,`movingTimeMs`,`steps`,`baselineSteps`,`stepOffset`,`lastHardwareSteps`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActivityEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getStartedAt());
        if (entity.getEndedAt() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getEndedAt());
        }
        statement.bindString(4, entity.getStatus());
        statement.bindDouble(5, entity.getDistanceMeters());
        statement.bindLong(6, entity.getElapsedMs());
        statement.bindLong(7, entity.getMovingTimeMs());
        statement.bindLong(8, entity.getSteps());
        statement.bindLong(9, entity.getBaselineSteps());
        statement.bindLong(10, entity.getStepOffset());
        statement.bindLong(11, entity.getLastHardwareSteps());
      }
    };
    this.__preparedStmtOfSaveProgress = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE activities SET distanceMeters = ?, elapsedMs = ?, movingTimeMs = ?,\n"
                + "           steps = ?, baselineSteps = ?, stepOffset = ?, lastHardwareSteps = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE activities SET status = ?, endedAt = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM activities WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ActivityEntity a, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfActivityEntity.insertAndReturnId(a);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object saveProgress(final long id, final double distance, final long elapsed,
      final long moving, final int steps, final long baseline, final int offset, final long last,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSaveProgress.acquire();
        int _argIndex = 1;
        _stmt.bindDouble(_argIndex, distance);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, elapsed);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, moving);
        _argIndex = 4;
        _stmt.bindLong(_argIndex, steps);
        _argIndex = 5;
        _stmt.bindLong(_argIndex, baseline);
        _argIndex = 6;
        _stmt.bindLong(_argIndex, offset);
        _argIndex = 7;
        _stmt.bindLong(_argIndex, last);
        _argIndex = 8;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSaveProgress.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setStatus(final long id, final String status, final Long endedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        if (endedAt == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, endedAt);
        }
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object byId(final long id, final Continuation<? super ActivityEntity> $completion) {
    final String _sql = "SELECT * FROM activities WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ActivityEntity>() {
      @Override
      @Nullable
      public ActivityEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDistanceMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceMeters");
          final int _cursorIndexOfElapsedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedMs");
          final int _cursorIndexOfMovingTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "movingTimeMs");
          final int _cursorIndexOfSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "steps");
          final int _cursorIndexOfBaselineSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineSteps");
          final int _cursorIndexOfStepOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "stepOffset");
          final int _cursorIndexOfLastHardwareSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "lastHardwareSteps");
          final ActivityEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final double _tmpDistanceMeters;
            _tmpDistanceMeters = _cursor.getDouble(_cursorIndexOfDistanceMeters);
            final long _tmpElapsedMs;
            _tmpElapsedMs = _cursor.getLong(_cursorIndexOfElapsedMs);
            final long _tmpMovingTimeMs;
            _tmpMovingTimeMs = _cursor.getLong(_cursorIndexOfMovingTimeMs);
            final int _tmpSteps;
            _tmpSteps = _cursor.getInt(_cursorIndexOfSteps);
            final long _tmpBaselineSteps;
            _tmpBaselineSteps = _cursor.getLong(_cursorIndexOfBaselineSteps);
            final int _tmpStepOffset;
            _tmpStepOffset = _cursor.getInt(_cursorIndexOfStepOffset);
            final long _tmpLastHardwareSteps;
            _tmpLastHardwareSteps = _cursor.getLong(_cursorIndexOfLastHardwareSteps);
            _result = new ActivityEntity(_tmpId,_tmpStartedAt,_tmpEndedAt,_tmpStatus,_tmpDistanceMeters,_tmpElapsedMs,_tmpMovingTimeMs,_tmpSteps,_tmpBaselineSteps,_tmpStepOffset,_tmpLastHardwareSteps);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object unfinished(final Continuation<? super ActivityEntity> $completion) {
    final String _sql = "SELECT * FROM activities WHERE status != 'FINISHED' ORDER BY id DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ActivityEntity>() {
      @Override
      @Nullable
      public ActivityEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDistanceMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceMeters");
          final int _cursorIndexOfElapsedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedMs");
          final int _cursorIndexOfMovingTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "movingTimeMs");
          final int _cursorIndexOfSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "steps");
          final int _cursorIndexOfBaselineSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineSteps");
          final int _cursorIndexOfStepOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "stepOffset");
          final int _cursorIndexOfLastHardwareSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "lastHardwareSteps");
          final ActivityEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final double _tmpDistanceMeters;
            _tmpDistanceMeters = _cursor.getDouble(_cursorIndexOfDistanceMeters);
            final long _tmpElapsedMs;
            _tmpElapsedMs = _cursor.getLong(_cursorIndexOfElapsedMs);
            final long _tmpMovingTimeMs;
            _tmpMovingTimeMs = _cursor.getLong(_cursorIndexOfMovingTimeMs);
            final int _tmpSteps;
            _tmpSteps = _cursor.getInt(_cursorIndexOfSteps);
            final long _tmpBaselineSteps;
            _tmpBaselineSteps = _cursor.getLong(_cursorIndexOfBaselineSteps);
            final int _tmpStepOffset;
            _tmpStepOffset = _cursor.getInt(_cursorIndexOfStepOffset);
            final long _tmpLastHardwareSteps;
            _tmpLastHardwareSteps = _cursor.getLong(_cursorIndexOfLastHardwareSteps);
            _result = new ActivityEntity(_tmpId,_tmpStartedAt,_tmpEndedAt,_tmpStatus,_tmpDistanceMeters,_tmpElapsedMs,_tmpMovingTimeMs,_tmpSteps,_tmpBaselineSteps,_tmpStepOffset,_tmpLastHardwareSteps);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ActivityEntity>> observeFinished() {
    final String _sql = "SELECT * FROM activities WHERE status = 'FINISHED' ORDER BY startedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"activities"}, new Callable<List<ActivityEntity>>() {
      @Override
      @NonNull
      public List<ActivityEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDistanceMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceMeters");
          final int _cursorIndexOfElapsedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "elapsedMs");
          final int _cursorIndexOfMovingTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "movingTimeMs");
          final int _cursorIndexOfSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "steps");
          final int _cursorIndexOfBaselineSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineSteps");
          final int _cursorIndexOfStepOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "stepOffset");
          final int _cursorIndexOfLastHardwareSteps = CursorUtil.getColumnIndexOrThrow(_cursor, "lastHardwareSteps");
          final List<ActivityEntity> _result = new ArrayList<ActivityEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActivityEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final double _tmpDistanceMeters;
            _tmpDistanceMeters = _cursor.getDouble(_cursorIndexOfDistanceMeters);
            final long _tmpElapsedMs;
            _tmpElapsedMs = _cursor.getLong(_cursorIndexOfElapsedMs);
            final long _tmpMovingTimeMs;
            _tmpMovingTimeMs = _cursor.getLong(_cursorIndexOfMovingTimeMs);
            final int _tmpSteps;
            _tmpSteps = _cursor.getInt(_cursorIndexOfSteps);
            final long _tmpBaselineSteps;
            _tmpBaselineSteps = _cursor.getLong(_cursorIndexOfBaselineSteps);
            final int _tmpStepOffset;
            _tmpStepOffset = _cursor.getInt(_cursorIndexOfStepOffset);
            final long _tmpLastHardwareSteps;
            _tmpLastHardwareSteps = _cursor.getLong(_cursorIndexOfLastHardwareSteps);
            _item = new ActivityEntity(_tmpId,_tmpStartedAt,_tmpEndedAt,_tmpStatus,_tmpDistanceMeters,_tmpElapsedMs,_tmpMovingTimeMs,_tmpSteps,_tmpBaselineSteps,_tmpStepOffset,_tmpLastHardwareSteps);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
