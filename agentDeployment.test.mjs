import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.env.DEPLOY_TEST_ROOT || '.';
const file = (relative) => join(root, relative);
const read = (relative) => readFileSync(file(relative), 'utf8');
// 断言前先剥掉注释行：否则把修复注释掉也能满足 assert.match（变异检验实测过两次）。
const stripComments = (text) => text.split('\n').filter((line) => !line.trimStart().startsWith('#')).join('\n');
const workflow = read('.github/workflows/deploy.yml');
const deploy = stripComments(read('deploy/deploy.sh'));
const nginx = stripComments(read('deploy/nginx.conf'));
const rollback = stripComments(read('deploy/rollback.sh'));
assert.ok(existsSync(file('deploy/AgentDockerfile')), 'Agent runtime image must be defined');
const dockerfile = readFileSync(file('deploy/AgentDockerfile'), 'utf8');
const agentConfig = readFileSync(file('cloud_agent/src/main/resources/application.yml'), 'utf8');
const backendConfig = readFileSync(file('cloud/src/main/resources/application.yml'), 'utf8');
const ragProperties = readFileSync(file('cloud/src/main/java/com/et/cloud/rag/RagProperties.java'), 'utf8');

assert.match(workflow, /working-directory: cloud_agent/, 'CI must build the Agent jar');
assert.match(workflow, /bundle\/agent\.jar/, 'CI must package the Agent jar');
assert.match(workflow, /bundle\/\.env/, 'CI must create the shared runtime environment');
assert.match(dockerfile, /COPY agent\.jar/, 'Agent image must contain the packaged jar');
assert.match(agentConfig, /name: \$\{RAG_LLM_FAST_MODEL:deepseek-flash\}/, 'local Agent default must use deepseek-flash');
assert.match(backendConfig, /fast-model: \$\{RAG_LLM_FAST_MODEL:deepseek-flash\}/, 'backend YAML default must use deepseek-flash');
assert.match(ragProperties, /fastModel = "deepseek-flash"/, 'backend Java default must use deepseek-flash');
assert.match(deploy, /agent\.jar/, 'deployment must require the Agent jar');
assert.match(deploy, /agent.*logs|logs.*agent/, 'deployment failure must show Agent logs');
assert.match(deploy, /docker compose exec -T nginx nginx -t/, 'deployment must validate the mounted Nginx config');
assert.match(deploy, /docker compose exec -T nginx nginx -s reload/, 'deployment must reload the mounted Nginx config');
assert.match(deploy, /logs --tail=60 nginx/, 'deployment health failure must show proxy diagnostics');
// 必须锚定行首：否则被注释掉的 resolver 也能骗过这条断言（变异检验实测过）。
assert.match(nginx, /^\s*resolver 127\.0\.0\.11/m, 'proxy must use Docker service discovery');
assert.match(nginx, /^\s*proxy_pass\s+http:\/\/\$/m, 'proxy_pass must use a variable so Nginx re-resolves the backend per request');
assert.match(nginx, /backend:8123/, 'proxy must still target the backend service on 8123');
assert.match(workflow, /for file in app\.jar agent\.jar docker-compose\.yml Dockerfile AgentDockerfile nginx\.conf; do/, 'CI must back up both jars, the stack definition and the proxy config');
// 漏打包 rollback.sh 时服务器上根本没有它，README 里的 `bash rollback.sh` 会直接失败
// （2026-10-16 实测：服务器 /opt/cloudwiki 下确实没有这个文件）。
// 只匹配「bundle/ 前一个就是 deploy/rollback.sh」：cp 命令跨两行，正则不能跨行匹配。
assert.match(workflow, /deploy\/rollback\.sh bundle\//, 'CI must ship the rollback script to the server');
assert.ok(workflow.indexOf('for file in app.jar agent.jar') < workflow.indexOf('tar xzf bundle.tar.gz'), 'backup must precede archive extraction');
assert.match(rollback, /for file in agent\.jar docker-compose\.yml Dockerfile AgentDockerfile nginx\.conf; do/, 'rollback must consider the previous Agent jar, stack definition and proxy config');
assert.match(rollback, /cp -f "\$file\.bak" "\$file"/, 'rollback must restore each backed-up file');
assert.match(rollback, /nginx -s reload/, 'rollback must reload the restored proxy config');

// 用异步 spawn 而不是 execFileSync：同步派生子进程在部分 Windows 沙箱里会被拦截（EBUSY）。
const run = (args, options = {}) => new Promise((done, reject) => {
  const child = spawn('docker', args, { stdio: ['ignore', 'pipe', 'pipe'], ...options });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', (chunk) => { stdout += chunk; });
  child.stderr.on('data', (chunk) => { stderr += chunk; });
  child.on('error', reject);
  child.on('close', (code) => (code === 0
    ? done(stdout)
    : reject(Object.assign(new Error(`docker ${args.join(' ')} exited ${code}: ${stderr}`), { stdout, stderr }))));
});

const env = {
  ...process.env,
  DB_PASSWORD: 'test-db',
  REDIS_PASSWORD: 'test-redis',
  DOC_PASSWORD: 'test-doc',
  RAG_LLM_API_KEY: 'test-model',
  AGENT_INTERNAL_KEY: 'test-shared-key',
};
const compose = JSON.parse(await run([
  'compose', '-f', file('deploy/docker-compose.yml'), 'config', '--format', 'json',
], { env }));
const backend = compose.services.backend;
const agent = compose.services.agent;
assert.ok(agent, 'Compose must run an Agent service');
assert.equal(backend.environment.AGENT_BASE_URL, 'http://agent:8124');
assert.equal(backend.environment.AGENT_INTERNAL_KEY, 'test-shared-key');
assert.equal(agent.environment.AGENT_INTERNAL_KEY, 'test-shared-key');
assert.equal(agent.environment.AGENT_BIND_ADDRESS, '0.0.0.0');
assert.equal(agent.environment.RAG_LLM_API_KEY, 'test-model');
assert.equal(agent.environment.RAG_LLM_FAST_MODEL, 'deepseek-flash');
assert.ok(!agent.ports || agent.ports.length === 0, 'Agent port must stay private');
await assert.rejects(run([
  'compose', '-f', file('deploy/docker-compose.yml'), 'config', '--format', 'json',
], { env: { ...env, AGENT_INTERNAL_KEY: '' } }), 'missing internal key must reject the deployment configuration');
console.log('Agent deployment contract: passed');
