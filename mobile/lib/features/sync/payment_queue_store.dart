import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'pending_payment.dart';

/// SQLite table of payments waiting to sync. Survives app restarts and log-outs.
class PaymentQueueStore {
  Database? _db;

  Future<Database> get _database async => _db ??= await openDatabase(
        p.join(await getDatabasesPath(), 'naanayam_queue.db'),
        version: 1,
        onCreate: (db, version) => db.execute('''
          CREATE TABLE pending_payment (
            client_txn_id TEXT PRIMARY KEY,
            user_id       INTEGER NOT NULL,
            loan_id       INTEGER NOT NULL,
            loan_no       TEXT NOT NULL,
            client_name   TEXT NOT NULL,
            amount        TEXT NOT NULL,
            mode          TEXT NOT NULL,
            upi_ref_no    TEXT,
            paid_on       TEXT NOT NULL,
            saved_at      TEXT NOT NULL,
            state         TEXT NOT NULL,
            error_code    TEXT,
            error_message TEXT,
            attempts      INTEGER NOT NULL DEFAULT 0
          )'''),
      );

  Future<List<PendingPayment>> forUser(int userId) async {
    final rows = await (await _database)
        .query('pending_payment', where: 'user_id = ?', whereArgs: [userId], orderBy: 'saved_at');
    return rows.map(PendingPayment.fromRow).toList();
  }

  /// Ignores a second save of the same clientTxnId (the payment is already queued).
  Future<void> insert(PendingPayment item) async =>
      (await _database).insert('pending_payment', item.toRow(), conflictAlgorithm: ConflictAlgorithm.ignore);

  Future<void> update(PendingPayment item) async => (await _database)
      .update('pending_payment', item.toRow(), where: 'client_txn_id = ?', whereArgs: [item.clientTxnId]);

  Future<void> delete(String clientTxnId) async =>
      (await _database).delete('pending_payment', where: 'client_txn_id = ?', whereArgs: [clientTxnId]);

  /// Applies a sync round in one transaction: removes accepted rows and stores updated ones.
  Future<void> apply({required Iterable<String> remove, required Iterable<PendingPayment> update}) async {
    await (await _database).transaction((txn) async {
      for (final id in remove) {
        await txn.delete('pending_payment', where: 'client_txn_id = ?', whereArgs: [id]);
      }
      for (final item in update) {
        await txn.update('pending_payment', item.toRow(), where: 'client_txn_id = ?', whereArgs: [item.clientTxnId]);
      }
    });
  }
}
