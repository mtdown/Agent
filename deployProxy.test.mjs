import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { assertSameProxy, cleanupOwned, configureProxy, createOwned, proxySnapshot, reportFailure, reportRetainedDirectory, withProxyDiagnostics } from './proxyLifecycle.mjs';

// 真实容器回归：复现 2026-10-16 的线上事故。
// 场景：后端容器被部署替换（IP 变化），Nginx 容器保持运行（未重启/未重建），
// 期望：/api 仍然可达，且请求路径 /api/ping 原样透传到后端。
// 用旧版写死 upstream 的配置跑这段，会在最后的断言上卡在 502 失败。

const run = (...args) => new Promise((done, reject) => {
  const child = spawn('docker', args, { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk; });
  child.stderr.on('data', (chunk) => { stderr += chunk; });
  child.on('error', reject);
  child.on('close', (code) => (code === 0
    ? done((args[0] === 'logs' ? stdout + stderr : stdout).trim())
    : reject(Object.assign(new Error(`docker ${args.join(' ')} exited ${code}`), { stdout, stderr, exitCode: code,
      operation: args.includes('-t') ? 'nginx-check' : args.includes('reload') ? 'nginx-reload' : 'regression' }))));
});
const docker = run;
// 允许非零退出（探测期间后端可能还没起来）
const tryRun = async (...args) => {
  try { return await run(...args); } catch { return null; }
};
const id = `cw-proxy-test-${process.pid}-${Math.random().toString(36).slice(2, 8)}`;
const network = id;
const backendA = `${id}-a`;
const backendB = `${id}-b`;
const filler = `${id}-filler`;
const proxy = `${id}-proxy`;
const temporary = join(tmpdir(), id);
const registry = [];
let proxyId;
let temporaryCreated = false;
const pause = (ms) => new Promise((done) => setTimeout(done, ms));

// 后端用 $request_uri 回显：既能区分打到的是 A 还是 B，
// 也能验证 Nginx 没有把 /api 前缀改写掉。
const serverConfig = (tag) => `server { listen 8123; location /api/ { return 200 "${tag}:$request_uri"; } }\n`;

async function request() {
  return tryRun('exec', proxy, 'wget', '-qO-', 'http://127.0.0.1:8080/api/ping');
}

async function waitFor(expected) {
  let actual;
  for (let attempt = 0; attempt < 15; attempt++) {
    actual = await request();
    if (actual === expected) return actual;
    await pause(1000);
  }
  return actual;
}

// 环境预检：CI（ubuntu-24.04 镜像）自带 Docker，本地则需先启动 Docker Desktop。
// 不做预检的话，daemon 不可用时只会在后面某一步抛出难懂的 spawn/connect 报错，
// 和「配置真的写错了」混在一起，很难区分是环境问题还是代码问题。
try {
  await docker('info', '--format', '{{.ServerVersion}}');
} catch (error) {
  console.error('容器回归测试需要可用的 Docker daemon，当前连不上。');
  console.error('  Docker daemon 未就绪或不可访问。');
  console.error('  本地：请先启动 Docker Desktop。');
  console.error('  CI：请确认 runner 镜像自带 Docker（ubuntu-24.04 镜像自带）。');
  process.exit(1);
}

await withProxyDiagnostics({ docker, proxy: () => proxyId, canDiagnose: () => registry.some(item => item.kind === 'container' && item.id === proxyId), work: async () => {
  mkdirSync(temporary);
  temporaryCreated = true;
  const configA = join(temporary, 'backend-a.conf');
  const configB = join(temporary, 'backend-b.conf');
  writeFileSync(configA, serverConfig('A'));
  writeFileSync(configB, serverConfig('B'));
  const label = `proxy-regression-owner=${id}`;
  const create = (kind, name, createArgs) => createOwned({ docker, kind, name, owner: id, registry, createArgs });
  await create('network', network, ['network', 'create', '--label', label, network]);
  const aId = await create('container', backendA, ['run', '-d', '--name', backendA, '--label', label, '--network', network, '--network-alias', 'backend',
    '--mount', `type=bind,source=${configA},target=/etc/nginx/conf.d/default.conf,readonly`, 'nginx:1.27-alpine']);
  try {
    proxyId = await create('container', proxy, ['run', '-d', '--name', proxy, '--label', label, '--network', network, 'nginx:1.27-alpine']);
  } catch (error) {
    proxyId = registry.find(item => item.kind === 'container' && item.id !== aId)?.id;
    throw error;
  }

  // 交付方式刻意模拟线上：nginx.conf 是 bind mount 的文件，替换后必须 reload 才生效。
  await configureProxy({ docker, proxy: proxyId, config: resolve('deploy/nginx.conf') });
  const beforeReplacement = await proxySnapshot(docker, proxyId);

  const initial = await waitFor('A:/api/ping');
  if (initial !== 'A:/api/ping') throw Object.assign(new Error('proxy cannot reach backend A with preserved path'), { operation: 'request' });

  // 换后端：先删 A，再用 filler 占住刚释放的 IP，逼 B 拿到一个不同的地址。
  await docker('rm', '-f', aId);
  registry.splice(registry.findIndex(item => item.id === aId), 1);
  await create('container', filler, ['run', '-d', '--name', filler, '--label', label, '--network', network, 'nginx:1.27-alpine']);
  await create('container', backendB, ['run', '-d', '--name', backendB, '--label', label, '--network', network, '--network-alias', 'backend',
    '--mount', `type=bind,source=${configB},target=/etc/nginx/conf.d/default.conf,readonly`, 'nginx:1.27-alpine']);

  const staleAddress = await docker('inspect', '-f', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', filler);
  const newAddress = await docker('inspect', '-f', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', backendB);
  assert.notEqual(staleAddress, newAddress, 'replacement backend must receive a different address');
  assert.equal(await docker('inspect', '-f', '{{.State.Restarting}}', proxy), 'false',
    'proxy container must keep running across backend replacement');

  // 关键断言：全程没有重启、没有 reload Nginx 容器。
  const result = await waitFor('B:/api/ping');
  if (result !== 'B:/api/ping') throw Object.assign(new Error('proxy cannot reach backend B with preserved path'), { operation: 'request' });
  assertSameProxy(beforeReplacement, await proxySnapshot(docker, proxyId));
  console.log(`Backend replacement proxy test: passed (backend ${staleAddress} -> ${newAddress}, proxy never restarted)`);
}, cleanup: async () => {
  try { await cleanupOwned({ docker, registry }); }
  finally { reportRetainedDirectory({ directory: temporary, created: temporaryCreated }); }
} }).catch(error => { reportFailure(error); process.exitCode = 1; });
