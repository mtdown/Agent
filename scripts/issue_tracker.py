"""Manage local issues using the Python standard library."""
import argparse
import json
import os
import sqlite3
import sys
import tempfile
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB = ROOT / '.local' / 'issue-tracker' / 'issues.db'
STATUSES = ('待处理', '待验证', '已关闭', '暂缓', '证据不足')
FIELDS = ('change_id', 'task_id', 'title', 'description', 'impact', 'status', 'resolution', 'evidence_path')
REQUIRED = ('change_id', 'task_id', 'title', 'description', 'impact')


def parser():
    p = argparse.ArgumentParser(description='本机问题日志管理')
    p.add_argument('--db', type=Path, default=DEFAULT_DB, help='数据库路径（须放在子命令之前）')
    commands = p.add_subparsers(dest='command', required=True)
    commands.add_parser('init', help='初始化数据库')
    backup = commands.add_parser('backup', help='安全备份数据库（不覆盖目标）')
    backup.add_argument('destination', type=Path)
    add = commands.add_parser('add', help='新增问题')
    for field in REQUIRED:
        add.add_argument('--' + field.replace('_', '-'), required=True)
    add.add_argument('--evidence-path', default='')
    show = commands.add_parser('show', help='查看问题')
    show.add_argument('id', type=int)
    listing = commands.add_parser('list', help='筛选问题')
    for field in ('change_id', 'task_id', 'status'):
        listing.add_argument('--' + field.replace('_', '-'), choices=STATUSES if field == 'status' else None)
    update = commands.add_parser('update', help='更新问题')
    update.add_argument('id', type=int)
    for field in FIELDS:
        update.add_argument('--' + field.replace('_', '-'), choices=STATUSES if field == 'status' else None)
    return p


def now():
    return datetime.now(timezone.utc).isoformat(timespec='microseconds')


def issue(conn, issue_id):
    row = conn.execute('SELECT * FROM issues WHERE id = ?', (issue_id,)).fetchone()
    if row is None:
        raise ValueError(f'问题不存在: {issue_id}')
    return dict(row)


def init_db(db):
    db.parent.mkdir(parents=True, exist_ok=True)
    schema = Path(__file__).with_name('issue_schema.sql').read_text(encoding='utf-8')
    # Build privately first; the hard link publishes atomically without replacement.
    with tempfile.TemporaryDirectory(prefix='.issue-init-', dir=db.parent) as tmp:
        staged = Path(tmp) / 'database.db'
        with closing(sqlite3.connect(staged)) as conn:
            conn.executescript(schema)
        os.link(staged, db)
    return {'database': str(db), 'initialized': True}


def backup_db(db, destination):
    destination = destination.resolve()
    if destination == db:
        raise ValueError('备份目标不能与源数据库相同')
    with tempfile.TemporaryDirectory(prefix='.issue-backup-', dir=destination.parent) as tmp:
        staged = Path(tmp) / 'backup.db'
        with closing(sqlite3.connect(db.as_uri() + '?mode=ro', uri=True)) as source:
            with closing(sqlite3.connect(staged)) as target:
                source.backup(target)
                if target.execute('PRAGMA quick_check').fetchall() != [('ok',)]:
                    raise ValueError('备份数据库完整性检查失败')
                table = target.execute("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'issues'").fetchone()
                columns = {row[1]: row for row in target.execute('PRAGMA table_info(issues)')}
                required = {'id', *FIELDS, 'created_at', 'updated_at'}
                if not table or not required.issubset(columns) or columns['id'][2].upper() != 'INTEGER' or columns['id'][5] != 1:
                    raise ValueError('备份源不是兼容的问题数据库：issues 表或必需字段缺失')
                target.execute('SELECT id, ' + ', '.join(FIELDS) + ', created_at, updated_at FROM issues LIMIT 1').fetchone()
        os.link(staged, destination)
    return {'database': str(db), 'backup': str(destination)}


def show_issue(conn, args):
    return issue(conn, args.id)


def list_issues(conn, args):
    filters = [(field, getattr(args, field)) for field in ('change_id', 'task_id', 'status') if getattr(args, field) is not None]
    where = ' AND '.join(field + ' = ?' for field, _ in filters)
    query = 'SELECT * FROM issues' + (' WHERE ' + where if where else '') + ' ORDER BY id'
    return [dict(row) for row in conn.execute(query, [value for _, value in filters])]


def add_issue(conn, args):
    for field in REQUIRED:
        if not getattr(args, field).strip():
            raise ValueError(f'{field} 不能为空')
    timestamp = now()
    values = [getattr(args, field) for field in REQUIRED]
    cursor = conn.execute('INSERT INTO issues (change_id, task_id, title, description, impact, status, evidence_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
                          (*values, '待处理', args.evidence_path, timestamp, timestamp))
    return issue(conn, cursor.lastrowid)


def update_issue(conn, args):
    changes = {field: getattr(args, field) for field in FIELDS if getattr(args, field) is not None}
    if not changes:
        raise ValueError('请至少提供一个需要更新的字段')
    conn.execute('BEGIN IMMEDIATE')
    current = issue(conn, args.id)
    merged = {**current, **changes}
    for field in REQUIRED:
        if not merged[field].strip():
            raise ValueError(f'{field} 不能为空')
    if changes.get('status') in ('待验证', '暂缓') and not (changes.get('resolution') or '').strip():
        raise ValueError('更新为待验证或暂缓时需要填写本次处理结论')
    if changes.get('status') == '已关闭' and (not (changes.get('resolution') or '').strip() or not (changes.get('evidence_path') or '').strip()):
        raise ValueError('关闭问题需要明确提供本次处理结论和验证证据位置')
    if merged['status'] == '已关闭' and (not (merged['resolution'] or '').strip() or not (merged['evidence_path'] or '').strip()):
        raise ValueError('已关闭问题的处理结论和证据位置不能为空')
    changes['updated_at'] = now()
    assignments = ', '.join(field + ' = ?' for field in changes)
    conn.execute(f'UPDATE issues SET {assignments} WHERE id = ?', (*changes.values(), args.id))
    return issue(conn, args.id)


def run(args):
    db = args.db.resolve()
    if args.command == 'init':
        return init_db(db)
    if not db.is_file():
        raise ValueError(f'数据库不存在，请先运行 init: {db}')
    if args.command == 'backup':
        return backup_db(db, args.destination)
    # mode=rw also prevents creating an empty database if the file disappears.
    with sqlite3.connect(db.as_uri() + '?mode=rw', uri=True) as conn:
        conn.row_factory = sqlite3.Row
        handlers = {'show': show_issue, 'list': list_issues, 'add': add_issue, 'update': update_issue}
        return handlers[args.command](conn, args)


def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    args = parser().parse_args()
    try:
        result = run(args)
    except (ValueError, OSError, sqlite3.Error) as exc:
        print(f'错误: {exc}', file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
