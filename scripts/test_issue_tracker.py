import json
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

SCRIPT = Path(__file__).with_name('issue_tracker.py')


class IssueTrackerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.db = Path(self.temp.name) / 'issues.db'

    def run_cli(self, *args, ok=True):
        result = subprocess.run([sys.executable, str(SCRIPT), '--db', str(self.db), *args],
                                capture_output=True, encoding='utf-8')
        self.assertEqual(result.returncode == 0, ok, result.stderr)
        return result

    def init_and_add(self):
        self.run_cli('init')
        return json.loads(self.run_cli('add', '--change-id', 'change-a', '--task-id', '1.1',
                                       '--title', '问题', '--description', '复现', '--impact', '影响').stdout)

    def test_init_add_show_and_filter(self):
        added = self.init_and_add()
        self.assertEqual(added['id'], 1)
        self.assertEqual(added['status'], '待处理')
        self.assertTrue(added['created_at'])
        self.assertEqual(json.loads(self.run_cli('show', '1').stdout)['title'], '问题')
        self.assertEqual(len(json.loads(self.run_cli('list', '--change-id', 'change-a', '--task-id', '1.1', '--status', '待处理').stdout)), 1)
        self.assertEqual(json.loads(self.run_cli('list', '--status', '已关闭').stdout), [])

    def test_update_and_failed_close_are_atomic(self):
        self.init_and_add()
        self.run_cli('update', '1', '--status', '待验证', '--resolution', '已自检')
        before = json.loads(self.run_cli('show', '1').stdout)
        self.run_cli('update', '1', '--status', '已关闭', '--resolution', '复测通过', ok=False)
        self.assertEqual(json.loads(self.run_cli('show', '1').stdout), before)
        self.run_cli('update', '1', '--status', '已关闭', '--resolution', '复测通过', '--evidence-path', 'report.md')
        after = json.loads(self.run_cli('show', '1').stdout)
        self.assertEqual((after['status'], after['resolution'], after['evidence_path']), ('已关闭', '复测通过', 'report.md'))

    def test_missing_db_does_not_create_it_and_init_is_not_destructive(self):
        self.run_cli('list', ok=False)
        self.assertFalse(self.db.exists())
        self.init_and_add()
        self.run_cli('init', ok=False)
        self.assertEqual(len(json.loads(self.run_cli('list').stdout)), 1)

    def test_invalid_status_and_missing_issue_do_not_modify_data(self):
        self.init_and_add()
        before = self.db.read_bytes()
        self.run_cli('update', '1', '--status', '不存在', ok=False)
        self.run_cli('update', '99', '--status', '待验证', ok=False)
        self.assertEqual(self.db.read_bytes(), before)

    def test_database_constraint_rolls_back_partial_update(self):
        self.init_and_add()
        with closing(sqlite3.connect(self.db)) as conn, conn:
            conn.execute("CREATE TRIGGER reject_resolution BEFORE UPDATE ON issues BEGIN SELECT RAISE(ABORT, 'rejected'); END")
        before = json.loads(self.run_cli('show', '1').stdout)
        self.run_cli('update', '1', '--status', '待验证', '--resolution', '已修复', ok=False)
        self.assertEqual(json.loads(self.run_cli('show', '1').stdout), before)

    def test_pending_verification_and_deferred_require_resolution(self):
        self.init_and_add()
        for status in ('待验证', '暂缓'):
            with self.subTest(status=status):
                before = json.loads(self.run_cli('show', '1').stdout)
                self.run_cli('update', '1', '--status', status, '--resolution', ' ', ok=False)
                self.assertEqual(json.loads(self.run_cli('show', '1').stdout), before)
        self.run_cli('update', '1', '--status', '待验证', '--resolution', '修复并自检')
        self.run_cli('update', '1', '--status', '暂缓', '--resolution', '人工决定暂缓')
        self.assertEqual(json.loads(self.run_cli('show', '1').stdout)['status'], '暂缓')

    def test_init_failure_can_be_retried_without_invalid_database(self):
        copy = Path(self.temp.name) / 'issue_tracker.py'
        shutil.copyfile(SCRIPT, copy)
        command = [sys.executable, str(copy), '--db', str(self.db), 'init']
        result = subprocess.run(command, capture_output=True, encoding='utf-8')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.db.exists())
        schema = copy.with_name('issue_schema.sql')
        schema.write_text('INVALID SQL;', encoding='utf-8')
        result = subprocess.run(command, capture_output=True, encoding='utf-8')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.db.exists())
        shutil.copyfile(SCRIPT.with_name('issue_schema.sql'), schema)
        result = subprocess.run(command, capture_output=True, encoding='utf-8')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_close_requires_new_explicit_evidence_and_resolution(self):
        self.init_and_add()
        self.run_cli('update', '1', '--evidence-path', 'old-failure.md')
        before = json.loads(self.run_cli('show', '1').stdout)
        for options in [('--resolution', '通过'), ('--evidence-path', 'retest.md'),
                        ('--resolution', '通过', '--evidence-path', ' ')]:
            self.run_cli('update', '1', '--status', '已关闭', *options, ok=False)
            self.assertEqual(json.loads(self.run_cli('show', '1').stdout), before)
        self.run_cli('update', '1', '--status', '已关闭', '--resolution', '通过', '--evidence-path', 'retest.md')
        self.run_cli('update', '1', '--title', '更新标题')
        self.assertEqual(json.loads(self.run_cli('show', '1').stdout)['title'], '更新标题')

    def test_closed_issue_cannot_be_left_with_blank_evidence_or_resolution(self):
        self.init_and_add()
        self.run_cli('update', '1', '--status', '已关闭', '--resolution', '通过', '--evidence-path', 'retest.md')
        before = json.loads(self.run_cli('show', '1').stdout)
        for field in ('--resolution', '--evidence-path'):
            with self.subTest(field=field):
                self.run_cli('update', '1', field, ' ', ok=False)
                self.assertEqual(json.loads(self.run_cli('show', '1').stdout), before)

    def test_backup_preserves_data_and_refuses_existing_targets(self):
        self.init_and_add()
        backup = Path(self.temp.name) / 'backup.db'
        self.run_cli('backup', str(backup))
        with closing(sqlite3.connect(backup)) as conn:
            self.assertEqual(conn.execute('SELECT title FROM issues').fetchone()[0], '问题')
        original = backup.read_bytes()
        self.run_cli('backup', str(backup), ok=False)
        self.assertEqual(backup.read_bytes(), original)
        source = self.db.read_bytes()
        self.run_cli('backup', str(self.db), ok=False)
        self.assertEqual(self.db.read_bytes(), source)

    def test_backup_rejects_empty_and_incompatible_problem_databases(self):
        backup = Path(self.temp.name) / 'backup.db'
        self.db.touch()
        sources = [b'']
        for content in sources:
            self.db.write_bytes(content)
            self.run_cli('backup', str(backup), ok=False)
            self.assertFalse(backup.exists())
            self.assertEqual(self.db.read_bytes(), content)
        with closing(sqlite3.connect(self.db)) as conn, conn:
            conn.execute('CREATE TABLE unrelated (id INTEGER)')
        self.run_cli('backup', str(backup), ok=False)
        self.assertFalse(backup.exists())
        with closing(sqlite3.connect(self.db)) as conn, conn:
            conn.execute('CREATE TABLE issues (id INTEGER)')
        self.run_cli('backup', str(backup), ok=False)
        self.assertFalse(backup.exists())


if __name__ == '__main__':
    unittest.main()
