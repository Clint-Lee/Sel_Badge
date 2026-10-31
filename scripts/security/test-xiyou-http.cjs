const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');

(async () => {
  const source = path.resolve(process.argv[2]);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'xiyou-security-'));
  fs.mkdirSync(path.join(directory, 'js'));
  fs.writeFileSync(path.join(directory, 'js', 'fixture.js'), 'console.log("public asset");');
  fs.writeFileSync(path.join(directory, 'index.html'), '<!doctype html><title>fixture</title>');
  fs.copyFileSync(source, path.join(directory, 'server.js'));
  const listener = net.createServer();
  await new Promise(resolve => listener.listen(0, '127.0.0.1', resolve));
  const port = listener.address().port;
  await new Promise(resolve => listener.close(resolve));
  const secret = crypto.randomBytes(32).toString('hex');
  const child = spawn(process.execPath, [path.join(directory, 'server.js')], {
    cwd: directory,
    env: { ...process.env, PORT: String(port), HOST: '127.0.0.1', XIYOU_PUBLIC_URL: 'https://example.test:8280', XIYOU_SESSION_SECRET: secret, XIYOU_OPEN_BROWSER: '0' },
    stdio: 'ignore',
  });
  const base = `http://127.0.0.1:${port}`;
  try {
    let ready = false;
    for (let n = 0; n < 60; n++) {
      try { await fetch(base, { redirect: 'manual' }); ready = true; break; } catch { await new Promise(resolve => setTimeout(resolve, 100)); }
    }
    assert.ok(ready, 'isolated test server starts');
    assert.equal((await fetch(base, { redirect: 'manual' })).status, 302);
    assert.equal((await fetch(base + '/js/fixture.js')).status, 200);
    for (const resource of ['/server.js', '/saves/.session-secret', '/.git/config', '/node_modules/package.json', '/scratch/test.js']) {
      assert.equal((await fetch(base + resource, { method: 'HEAD' })).status, 404, resource);
    }
    const sso = await fetch(base + '/auth/badge', { redirect: 'manual' });
    assert.match(sso.headers.get('set-cookie'), /; Secure/);
    const request = origin => fetch(base + '/api/save', { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: '{}' });
    assert.equal((await request('https://example.test:8280')).status, 401);
    assert.equal((await request('https://attacker.test')).status, 403);
    console.log('Game HTTP security: private paths, public assets, login gate, Secure cookies and exact origin passed');
  } finally {
    child.kill();
    await new Promise(resolve => child.once('exit', resolve));
    fs.rmSync(directory, { recursive: true, force: true });
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
