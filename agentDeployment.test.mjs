import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.env.DEPLOY_TEST_ROOT || '.';
const file = (relative) => join(root, relative);
const workflow = readFileSync(file('.github/workflows/deploy.yml'), 'utf8');
const deploy = readFileSync(file('deploy/deploy.sh'), 'utf8');
const rollback = readFileSync(file('deploy/rollback.sh'), 'utf8');
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
assert.match(workflow, /for file in app\.jar agent\.jar docker-compose\.yml Dockerfile AgentDockerfile; do/, 'CI must back up both jars and the stack definition');
assert.ok(workflow.indexOf('for file in app.jar agent.jar') < workflow.indexOf('tar xzf bundle.tar.gz'), 'backup must precede archive extraction');
assert.match(rollback, /for file in agent\.jar docker-compose\.yml Dockerfile AgentDockerfile; do/, 'rollback must consider the previous Agent jar and stack definition');
assert.match(rollback, /cp -f "\$file\.bak" "\$file"/, 'rollback must restore each backed-up file');

const env = {
  ...process.env,
  DB_PASSWORD: 'test-db',
  REDIS_PASSWORD: 'test-redis',
  DOC_PASSWORD: 'test-doc',
  RAG_LLM_API_KEY: 'test-model',
  AGENT_INTERNAL_KEY: 'test-shared-key',
};
const compose = JSON.parse(execFileSync('docker', [
  'compose', '-f', file('deploy/docker-compose.yml'), 'config', '--format', 'json',
], { env, encoding: 'utf8' }));
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
assert.throws(() => execFileSync('docker', [
  'compose', '-f', file('deploy/docker-compose.yml'), 'config', '--format', 'json',
], { env: { ...env, AGENT_INTERNAL_KEY: '' }, stdio: 'pipe' }), 'missing internal key must reject the deployment configuration');
console.log('Agent deployment contract: passed');
