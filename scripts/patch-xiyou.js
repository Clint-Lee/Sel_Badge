const fs = require('fs');

const serverFile = 'D:/Extends/antigravityProj/XiYouOnline/server.js';
let content = fs.readFileSync(serverFile, 'utf8');

const targetStr = "let reqPath = decodeURIComponent(route);\r\n      if (reqPath === '/' || reqPath === '') reqPath = '/index.html';";
const targetStrLF = "let reqPath = decodeURIComponent(route);\n      if (reqPath === '/' || reqPath === '') reqPath = '/index.html';";

const replacement = `let reqPath = decodeURIComponent(route);
      if (reqPath === '/' || reqPath === '') reqPath = '/index.html';

      // ── 统一登录守卫：游戏必须登录后才能进入 ──
      if (reqPath === '/index.html') {
        const session = readSession(req);
        if (!session) {
          res.writeHead(302, { Location: '/auth/badge' });
          res.end();
          return;
        }
      }`;

if (content.includes(targetStr)) {
  content = content.replace(targetStr, replacement.replace(/\n/g, '\r\n'));
} else if (content.includes(targetStrLF)) {
  content = content.replace(targetStrLF, replacement);
}

fs.writeFileSync(serverFile, content, 'utf8');
console.log('XiYouOnline server.js login gate added successfully');

