export async function configureProxy({ docker, proxy, config, attempts = 30, pause = ms => new Promise(resolve => setTimeout(resolve, ms)) }) {
  let ready = false;
  for (let attempt = 0; attempt < attempts; attempt++) {
    try {
      await docker('exec', proxy, 'wget', '-qO-', 'http://127.0.0.1/');
      ready = true;
      break;
    } catch {
      const running = await docker('inspect', '-f', '{{.State.Running}}', proxy);
      if (running !== 'true') throw Object.assign(new Error('proxy exited before readiness'), { operation: 'proxy-exited' });
      if (attempt < attempts - 1) await pause(1000);
    }
  }
  if (!ready) throw Object.assign(new Error('proxy readiness timed out'), { operation: 'proxy-timeout' });
  await docker('cp', config, `${proxy}:/etc/nginx/conf.d/default.conf`);
  await docker('exec', proxy, 'nginx', '-t');
  await docker('exec', proxy, 'nginx', '-s', 'reload');
}

function safeLog(output) {
  const lines = output.split(/\r?\n/).filter(Boolean);
  const categories = [
    [/unknown directive/i, 'unknown-directive'],
    [/invalid number of arguments/i, 'invalid-argument-count'],
    [/cannot load certificate/i, 'certificate-load'],
    [/host not found in upstream/i, 'upstream-resolution'],
    [/syntax is ok/i, 'syntax-ok'],
  ];
  const summaries = [];
  for (const line of lines) {
    const severity = line.match(/\[(emerg|alert|crit|error|warn|notice|info|debug)\]/i)?.[1]?.toLowerCase();
    const category = categories.find(([pattern]) => pattern.test(line))?.[1];
    if (!severity && !category) continue;
    const location = line.match(/(?:^|\s|\")((?:\/etc\/nginx\/nginx\.conf|\/etc\/nginx\/conf\.d\/default\.conf)):(\d{1,6})(?=$|\D)/);
    summaries.push(`nginx ${severity || 'info'} category=${category || 'other'}${location ? ` file=${location[1]} line=${location[2]}` : ''}`);
  }
  return summaries.length ? summaries.join('\n') : `no recognized nginx events (${lines.length} lines hidden)`;
}

export function reportFailure(error, log = console.error) {
  const allowed = new Set(['nginx-check', 'nginx-reload', 'proxy-exited', 'proxy-timeout', 'create', 'request', 'regression']);
  const operation = allowed.has(error?.operation) ? error.operation : 'regression';
  const code = Number.isInteger(error?.exitCode) && error.exitCode >= 0 && error.exitCode <= 255 ? `; exit code ${error.exitCode}` : '';
  log(`Proxy regression failed (${operation}${code}); see safe diagnostics above.`);
}

export async function withProxyDiagnostics({ docker, proxy, work, cleanup, log = console.error, canDiagnose = () => true }) {
  let failed = false;
  try {
    return await work();
  } catch (error) {
    failed = true;
    if (typeof error?.stderr === 'string' && error.stderr) {
      try { log(`command diagnostics: ${safeLog(error.stderr)}`); } catch {}
    }
    for (const [label, args] of [
      ['proxy state and exit code', ['inspect', '-f', '{{.State.Status}} {{.State.ExitCode}}']],
      ['proxy logs', ['logs']],
    ]) {
      try {
        if (!canDiagnose()) throw new Error('proxy ownership unavailable');
        const result = await docker(...args, typeof proxy === 'function' ? proxy() : proxy);
        const state = /^(created|restarting|running|removing|paused|exited|dead) (-?\d{1,10})$/.exec(result.trim());
        log(`${label}: ${label === 'proxy logs' ? safeLog(result) : state ? `${state[1]} ${state[2]}` : 'unavailable'}`);
      }
      catch { try { log(`${label}: unavailable`); } catch {} }
    }
    throw error;
  } finally {
    try { await cleanup(); } catch (error) { if (!failed) throw error; }
  }
}

export async function createOwned({ docker, kind, name, owner, registry, createArgs, log = console.error }) {
  try {
    const id = await docker(...createArgs);
    registry.push({ kind, id });
    return id;
  } catch (original) {
    if (original && typeof original === 'object') original.operation = 'create';
    try {
      const format = kind === 'network'
        ? '{{index .Labels "proxy-regression-owner"}} {{.Id}}'
        : '{{index .Config.Labels "proxy-regression-owner"}} {{.Id}}';
      const result = kind === 'network'
        ? await docker('network', 'inspect', '-f', format, name)
        : await docker('inspect', '-f', format, name);
      const [actualOwner, id] = result.split(/\s+/);
      if (actualOwner === owner && id) registry.push({ kind, id });
      else log(`Resource ownership unconfirmed; possible residual ${kind}: ${name}`);
    } catch {
      try { log(`Resource ownership unconfirmed; possible residual ${kind}: ${name}`); } catch {}
    }
    throw original;
  }
}

export async function cleanupOwned({ docker, registry, log = console.error }) {
  for (const { kind, id } of [...registry].reverse()) {
    try {
      if (kind === 'network') await docker('network', 'rm', id);
      else await docker('rm', '-f', id);
    } catch {
      try { log(`Failed to remove owned ${kind}; possible residual: ${id}`); } catch {}
    }
  }
}

export function assertSameProxy(before, after) {
  if (!before || !after || !before.id || !before.startedAt || before.id !== after.id ||
      before.startedAt !== after.startedAt || before.restartCount !== after.restartCount) {
    throw new Error('proxy identity or restart state changed during backend replacement');
  }
}

export async function proxySnapshot(docker, proxy) {
  const value = await docker('inspect', '-f', '{{.Id}} {{.State.StartedAt}} {{.RestartCount}}', proxy);
  const [id, startedAt, count] = value.split(/\s+/);
  if (!id || !startedAt || !/^\d+$/.test(count || '')) throw new Error('proxy snapshot unavailable');
  return { id, startedAt, restartCount: Number(count) };
}

export function reportRetainedDirectory({ directory, created, platform = process.platform, log = console.log }) {
  if (!created) return;
  const command = platform === 'win32'
    ? `Remove-Item -LiteralPath '${directory.replaceAll("'", "''")}' -Recurse -Force`
    : `rm -rf -- '${directory.replaceAll("'", "'\\''")}'`;
  log(`Retained test directory: ${directory}`);
  log(`Administrator cleanup (after acceptance): ${command}`);
}
