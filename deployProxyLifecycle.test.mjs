import test from 'node:test';
import assert from 'node:assert/strict';
import { configureProxy, withProxyDiagnostics, cleanupOwned, createOwned, reportFailure, assertSameProxy, reportRetainedDirectory } from './proxyLifecycle.mjs';

test('reports retained directory and exact platform-specific command only after creation', () => {
  const output = [];
  reportRetainedDirectory({ directory: "C:\\Users\\O'Brien\\Temp\\cw-proxy-test-1", created: true, platform: 'win32', log: x => output.push(x) });
  assert.deepEqual(output, ["Retained test directory: C:\\Users\\O'Brien\\Temp\\cw-proxy-test-1", "Administrator cleanup (after acceptance): Remove-Item -LiteralPath 'C:\\Users\\O''Brien\\Temp\\cw-proxy-test-1' -Recurse -Force"]);
  output.length = 0;
  reportRetainedDirectory({ directory: "/tmp/O'Brien/cw-proxy-test-1", created: true, platform: 'linux', log: x => output.push(x) });
  assert.deepEqual(output, ["Retained test directory: /tmp/O'Brien/cw-proxy-test-1", "Administrator cleanup (after acceptance): rm -rf -- '/tmp/O'\\''Brien/cw-proxy-test-1'"]);
  output.length = 0;
  reportRetainedDirectory({ directory: '/tmp/not-created', created: false, log: x => output.push(x) });
  assert.deepEqual(output, []);
});

for (const fail of [false, true]) {
  test(`retained directory is reported when work ${fail ? 'fails' : 'succeeds'}`, async () => {
    const output = [];
    const result = withProxyDiagnostics({ docker: async () => '', proxy: 'own', canDiagnose: () => false, log: () => {},
      work: async () => { if (fail) throw new Error('original'); },
      cleanup: async () => reportRetainedDirectory({ directory: '/tmp/cw-proxy-test-1', created: true, platform: 'linux', log: x => output.push(x) }),
    });
    if (fail) await assert.rejects(result, /original/); else await result;
    assert.equal(output.length, 2);
    assert.ok(output[1].endsWith("rm -rf -- '/tmp/cw-proxy-test-1'"));
  });
}

test('proxy restart or replacement is detected after recovery', () => {
  const before = { id: 'abc', startedAt: 'one', restartCount: 0 };
  assert.doesNotThrow(() => assertSameProxy(before, { ...before }));
  assert.throws(() => assertSameProxy(before, { ...before, startedAt: 'two', restartCount: 1 }));
  assert.throws(() => assertSameProxy(before, { ...before, id: 'other' }));
});

test('safe failure output distinguishes each operation and numeric exit code', () => {
  for (const operation of ['proxy-exited', 'proxy-timeout', 'create', 'nginx-check', 'nginx-reload', 'request']) {
    const output = [];
    reportFailure({ operation, exitCode: 42, message: 'opaque-ff888' }, value => output.push(value));
    assert.match(output[0], new RegExp(operation));
    assert.match(output[0], /42/);
    assert.doesNotMatch(output[0], /opaque-ff888/);
  }
});

test('top-level failure output never serializes exception fields or message', () => {
  const output = [];
  const error = Object.assign(new Error('opaque-9ac48'), { stdout: 'opaque-1fe22', stderr: 'opaque-77c23', operation: 'nginx-check' });
  reportFailure(error, value => output.push(value));
  assert.doesNotMatch(output.join('\n'), /opaque-/);
  assert.match(output.join('\n'), /nginx-check/);
  reportFailure({ operation: 'opaque-8ee11' }, value => output.push(value));
  assert.doesNotMatch(output.join('\n'), /opaque-/);
});

test('configuration error stderr yields a safe summary while preserving original exception', async () => {
  const output = [];
  const original = Object.assign(new Error('opaque-random-z17'), {
    stderr: 'nginx: [emerg] unknown directive "opaque-random-z17" in /etc/nginx/conf.d/default.conf:19',
  });
  await assert.rejects(withProxyDiagnostics({ docker: async (...args) => args[0] === 'logs' ? '' : 'exited 1', proxy: 'own',
    work: async () => { throw original; }, cleanup: async () => {}, log: s => output.push(s),
  }), error => error === original);
  assert.doesNotMatch(output.join('\n'), /opaque-random-z17/);
  assert.match(output.join('\n'), /unknown-directive.*default.conf.*19/);
});

for (const kind of ['container', 'network']) {
  for (const ownership of ['owned', 'missing', 'foreign', 'unknown']) {
    test(`${kind} creation failure handles ${ownership} ownership`, async () => {
      const original = new Error('original creation failure');
      const calls = [];
      const warnings = [];
      const registry = [];
      const docker = async (...args) => {
        calls.push(args);
        if (args.includes('inspect')) {
          if (ownership === 'unknown' || ownership === 'missing') throw new Error('inspect failed');
          return `${ownership === 'owned' ? 'owner-token' : 'other-owner'} resource-id`;
        }
        if (args[0] === 'rm' || (args[0] === 'network' && args[1] === 'rm')) return '';
        throw original;
      };
      await assert.rejects(createOwned({ docker, kind, name: 'name', owner: 'owner-token', registry,
        createArgs: ['create'], log: message => warnings.push(message) }), error => error === original);
      if (ownership === 'owned') assert.deepEqual(registry, [{ kind, id: 'resource-id' }]);
      else {
        assert.deepEqual(registry, []);
        assert.ok(warnings.some(x => /unconfirmed|residual/i.test(x)));
      }
      assert.ok(calls.some(x => x.includes('inspect')));
      await cleanupOwned({ docker, registry });
      const removes = calls.filter(x => x[0] === 'rm' || (x[0] === 'network' && x[1] === 'rm'));
      assert.equal(removes.length, ownership === 'owned' ? 1 : 0);
      if (removes.length) assert.equal(removes[0].at(-1), 'resource-id');
    });
  }
}

function dockerDouble({ readyAfter = 0, exit = false, fail = '', diagnosticFailure = false } = {}) {
  const calls = [];
  let probes = 0;
  const docker = async (...args) => {
    calls.push(args);
    const command = args.join(' ');
    if (command.includes('wget')) {
      if (probes++ < readyAfter || exit) throw new Error('not ready');
      return 'welcome';
    }
    if (command.startsWith('inspect')) {
      if (diagnosticFailure && command.includes('ExitCode')) throw new Error('diagnostic failed');
      return command.includes('ExitCode') ? 'exited 17' : exit ? 'false' : 'true';
    }
    if (command.startsWith('logs')) return 'proxy startup log';
    if (fail && command.includes(fail)) throw new Error(`original ${fail}`);
    return '';
  };
  return { docker, calls };
}

test('diagnostics only expose safe log categories and never unknown secrets', async () => {
  const calls = [];
  const output = [];
  const docker = async (...args) => {
    calls.push(args);
    if (args[0] === 'logs') return 'opaque-7d3cc8f691\nnginx: [emerg] unknown directive "opaque-7d3cc8f691" in /etc/nginx/conf.d/default.conf:42';
    return 'exited 7';
  };
  for (const canDiagnose of [() => false, () => true]) {
    await assert.rejects(withProxyDiagnostics({ docker, proxy: 'owned-proxy-id', canDiagnose,
      work: async () => { throw new Error('original'); }, cleanup: async () => {}, log: value => output.push(value) }), /original/);
  }
  assert.ok(calls.every(x => x.includes('owned-proxy-id')));
  assert.equal(calls.filter(x => x[0] === 'logs').length, 1);
  assert.doesNotMatch(output.join('\n'), /opaque-7d3cc8f691/);
  assert.match(output.join('\n'), /emerg.*unknown.directive.*\/etc\/nginx\/conf.d\/default.conf.*42/i);
});

test('failed proxy creation diagnoses and cleans only verified immutable id', async () => {
  const calls = [];
  const output = [];
  const registry = [];
  let proxyId;
  const original = new Error('original run failure');
  const docker = async (...args) => {
    calls.push(args);
    if (args[0] === 'run') throw original;
    if (args[0] === 'inspect' && args[2].includes('Config.Labels')) return 'owner-token owned-id';
    if (args[0] === 'inspect') return 'created 127';
    if (args[0] === 'logs') return 'proxy startup log';
    return '';
  };
  await assert.rejects(withProxyDiagnostics({ docker, proxy: () => proxyId, canDiagnose: () => Boolean(proxyId), log: s => output.push(s),
    work: async () => {
      try { proxyId = await createOwned({ docker, kind: 'container', name: 'proxy', owner: 'owner-token', registry, createArgs: ['run'] }); }
      catch (error) { proxyId = registry[0]?.id; throw error; }
    }, cleanup: () => cleanupOwned({ docker, registry }),
  }), error => error === original);
  assert.deepEqual(calls.slice(-3), [['inspect', '-f', '{{.State.Status}} {{.State.ExitCode}}', 'owned-id'], ['logs', 'owned-id'], ['rm', '-f', 'owned-id']]);
  assert.match(output.join('\n'), /created 127/);
});

test('waits for default HTTP before copying, validating and reloading', async () => {
  const { docker, calls } = dockerDouble({ readyAfter: 2 });
  await configureProxy({ docker, proxy: 'test-proxy', config: 'config', attempts: 4, pause: async () => {} });
  assert.deepEqual(calls.map(x => x[0]), ['exec', 'inspect', 'exec', 'inspect', 'exec', 'cp', 'exec', 'exec']);
  assert.match(calls[2].join(' '), /wget/);
  assert.match(calls[6].join(' '), /nginx -t/);
  assert.match(calls[7].join(' '), /nginx -s reload/);
});

for (const scenario of [
  { name: 'exit', options: { exit: true }, message: /exited before readiness/ },
  { name: 'timeout', options: { readyAfter: 99 }, message: /timed out/ },
  { name: 'invalid config', options: { fail: 'nginx -t' }, message: /original nginx -t/ },
  { name: 'reload failure', options: { fail: 'nginx -s reload' }, message: /original nginx -s reload/ },
  { name: 'diagnostic failure', options: { fail: 'nginx -t', diagnosticFailure: true }, message: /original nginx -t/ },
]) {
  test(`${scenario.name}: preserves failure, captures diagnostics before own cleanup`, async () => {
    const { docker, calls } = dockerDouble(scenario.options);
    const output = [];
    await assert.rejects(withProxyDiagnostics({
      docker, proxy: 'test-proxy', log: value => output.push(value),
      work: () => configureProxy({ docker, proxy: 'test-proxy', config: 'config', attempts: 2, pause: async () => {} }),
      cleanup: async () => { await docker('rm', '-f', 'test-proxy'); await docker('network', 'rm', 'test-network'); },
    }), scenario.message);
    const cleanup = calls.findIndex(x => x[0] === 'rm');
    assert.ok(cleanup > calls.findIndex(x => x[0] === 'logs'));
    assert.ok(calls.some(x => x[0] === 'inspect' && x.join(' ').includes('ExitCode')));
    assert.ok(output.some(x => x.includes('proxy logs:')));
    if (!scenario.options.diagnosticFailure) assert.ok(output.some(x => x.includes('exited 17')));
    assert.deepEqual(calls.slice(cleanup), [['rm', '-f', 'test-proxy'], ['network', 'rm', 'test-network']]);
    if (scenario.name === 'exit' || scenario.name === 'timeout') assert.ok(!calls.some(x => x[0] === 'cp'));
  });
}
