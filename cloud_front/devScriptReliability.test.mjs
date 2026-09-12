import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

const [stopSource, startSource, uploadSource] = await Promise.all([
  readSource('../stop-dev.ps1'),
  readSource('../start-dev.ps1'),
  readSource('../upload.ps1'),
])

test('stop script no longer depends on netstat or taskkill', () => {
  assert.doesNotMatch(stopSource, /netstat/i)
  assert.doesNotMatch(stopSource, /taskkill/i)
  assert.match(stopSource, /Get-NetTCPConnection/)
  assert.match(stopSource, /Stop-Process/)
})

test('stop script never swallows errors', () => {
  assert.doesNotMatch(stopSource, /2>\$null/)
  assert.doesNotMatch(stopSource, /Out-Null/)
  assert.match(stopSource, /Stop-Process -Id \$procId -Force -ErrorAction Stop/)
  assert.match(stopSource, /catch \{/)
  assert.match(stopSource, /\$failures\.Add/)
})

test('stop script treats "no listener" as success instead of a query failure', () => {
  // Get-NetTCPConnection throws CmdletizationQuery_NotFound when a port has no
  // listener at all; treating that as an error would report a clean machine as
  // broken and exit 1 every time.
  assert.match(stopSource, /function Test-NoListenerError/)
  assert.match(stopSource, /CmdletizationQuery_NotFound/)
  assert.match(stopSource, /if \(Test-NoListenerError \$\_\)/)
})

test('stop script re-checks the port after stopping and fails loudly', () => {
  assert.match(stopSource, /function Test-PortListening/)
  assert.match(stopSource, /Start-Sleep -Seconds 2/)
  assert.match(stopSource, /Port \$port is still listening and could not be released/)
  assert.match(stopSource, /Unable to query port/)
  assert.match(stopSource, /exit 1/)
  assert.match(stopSource, /exit 0/)
})

test('dev scripts stay ASCII-only so PowerShell 5.1 cannot misdecode them', () => {
  for (const [name, source] of [
    ['stop-dev.ps1', stopSource],
    ['start-dev.ps1', startSource],
    ['upload.ps1', uploadSource],
  ]) {
    const nonAscii = source.match(/[^\x09\x0a\x0d\x20-\x7e]/g)
    assert.equal(nonAscii, null, `${name} contains non-ASCII characters: ${nonAscii?.join('')}`)
  }
})

test('start script auto-cleans occupied dev ports before starting', () => {
  assert.match(startSource, /function Clear-DevPort/)
  assert.match(startSource, /foreach \(\$p in @\(\$backendPort, \$frontPort\)\)/)
  assert.match(startSource, /Port \$Port in use by/)
  assert.match(startSource, /Stop-Process -Id \$procId -Force/)
  assert.match(startSource, /still occupied after cleanup/)
  assert.match(startSource, /if \(-not \$portsClear\) \{ exit 1 \}/)
})

test('start script tunnels via cloudflared only', () => {
  assert.doesNotMatch(startSource, /cpolar|ngrok|authtoken/i)
  assert.match(startSource, /trycloudflare/)
  assert.match(startSource, /winget install Cloudflare\.cloudflared/)
})

test('start script stops everything on Enter', () => {
  assert.match(startSource, /function Stop-DevServices/)
  assert.match(startSource, /Press Enter to STOP all services/)
  assert.match(startSource, /Stop-DevTunnel/)
})

test('start script keeps the maven invocation untouched', () => {
  assert.match(startSource, /mvn -DskipTests package -q/)
})

test('upload script detects an unusable git instead of failing silently', () => {
  assert.match(uploadSource, /function Assert-GitAvailable/)
  assert.match(uploadSource, /git --version/)
  assert.match(uploadSource, /git is not available in this session/)
  assert.match(uploadSource, /Unable to determine the current branch/)
})

test('upload script verifies the push actually landed', () => {
  assert.match(uploadSource, /Run-Git @\('fetch', 'origin'\)/)
  assert.match(uploadSource, /rev-list/)
  assert.match(uploadSource, /Push did not take effect/)
  assert.match(uploadSource, /is up to date with HEAD/)
})
