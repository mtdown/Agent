import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

function read(path) {
  return readFileSync(path, 'utf8');
}

function runPowerShell(script, args = []) {
  return execFileSync(
    'powershell',
    ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script, ...args],
    { encoding: 'utf8' },
  );
}

function assertContains(source, expected, message) {
  assert.ok(source.includes(expected), message);
}

const checks = [
  ['.gitignore excludes local-only files', () => {
    const ignore = read('.gitignore');
    for (const pattern of [
      '.workbuddy/',
      'tmp/',
      '*.inspect.ndjson',
      'diag.txt',
      'cloud/project.txt',
    ]) {
      assertContains(ignore, pattern, `.gitignore should contain ${pattern}`);
    }
    assert.ok(
      ignore.includes('.idea') || ignore.includes('.idea/'),
      '.gitignore should ignore .idea',
    );
  }],

  ['start-task.ps1 creates remote branch before local tracking branch', () => {
    assert.ok(existsSync('start-task.ps1'), 'start-task.ps1 should exist');
    const output = runPowerShell('.\\start-task.ps1', [
      '-Type',
      'feature',
      '-Name',
      'upload-workflow-test',
      '-DryRun',
    ]);

    assertContains(output, 'git fetch origin', 'script should fetch origin first');
    assertContains(
      output,
      'git push origin origin/main:refs/heads/feature/upload-workflow-test',
      'script should create remote branch from origin/main',
    );
    assertContains(
      output,
      'git switch --track -c feature/upload-workflow-test',
      'script should create local tracking branch after remote branch exists',
    );
  }],

  ['upload.ps1 uploads only tracked non-main branch and does not merge', () => {
    assert.ok(existsSync('upload.ps1'), 'upload.ps1 should exist');
    const source = read('upload.ps1');
    const output = runPowerShell('.\\upload.ps1', [
      '-Message',
      'test: upload workflow dry run',
      '-DryRun',
    ]);

    assertContains(output, 'Current branch:', 'upload should show current branch');
    assertContains(output, 'Upstream branch:', 'upload should show upstream branch');
    assertContains(output, 'git status --short', 'upload should show status check');
    assertContains(output, 'git push', 'upload should push upstream branch');
    assertContains(output, 'merge request', 'upload should print merge-request guidance');
    assert.ok(!/git\s+merge\b/.test(source), 'upload script must not merge branches');
    assert.ok(!/pr\s+merge\b/.test(source), 'upload script must not merge pull requests');
  }],

  ['AGENTS.md requires remote-first branch and upload scripts', () => {
    const agents = read('AGENTS.md');
    assertContains(agents, 'start-task.ps1', 'AGENTS.md should mention start-task.ps1');
    assertContains(agents, 'upload.ps1', 'AGENTS.md should mention upload.ps1');
    assertContains(agents, '远程', 'AGENTS.md should describe remote-first branch flow');
    assertContains(agents, 'merge request', 'AGENTS.md should preserve review flow');
  }],

  ['main preview scripts are manual acceptance only', () => {
    assert.ok(existsSync('start-main-dev.ps1'), 'start-main-dev.ps1 should exist');
    assert.ok(existsSync('stop-main-dev.ps1'), 'stop-main-dev.ps1 should exist');

    const startMain = read('start-main-dev.ps1');
    const stopMain = read('stop-main-dev.ps1');
    const agents = read('AGENTS.md');

    assertContains(startMain, 'main', 'main start script should target main');
    assertContains(startMain, 'git pull --ff-only origin main', 'main start script should sync latest remote main');
    assertContains(startMain, 'start-dev.ps1', 'main start script should delegate to normal dev start script');
    assertContains(stopMain, 'stop-dev.ps1', 'main stop script should delegate to normal dev stop script');
    assertContains(agents, '手动验收', 'AGENTS.md should state main preview is manual acceptance');
    assertContains(
      agents,
      'AI 编码助手开发和测试当前任务时，仍必须使用当前任务分支目录下的 `stop-dev.ps1` 和 `start-dev.ps1`',
      'AGENTS.md should keep agent development on current branch scripts',
    );
  }],
];

let failures = 0;
for (const [name, check] of checks) {
  try {
    check();
    console.log(`PASS ${name}`);
  } catch (error) {
    failures += 1;
    console.error(`FAIL ${name}`);
    console.error(error.message);
  }
}

if (failures > 0) {
  process.exit(1);
}
