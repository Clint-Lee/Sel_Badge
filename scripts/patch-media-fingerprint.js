const fs = require('fs');

// 1. Update guest-access.js
const guestAccessPath = 'D:/Extends/antigravityProj/MediaTools/Media_Tools/lib/guest-access.js';
let guestContent = fs.readFileSync(guestAccessPath, 'utf8');

// Change LIMIT from 20 to 3
guestContent = guestContent.replace(
  "const COOKIE='hotpot_visitor',TTL=8*3600000,LIMIT=20,IP_LIMIT=60,MAX_EMPTY_PER_IP=20;",
  "const COOKIE='hotpot_visitor',TTL=8*3600000,LIMIT=3,IP_LIMIT=60,MAX_EMPTY_PER_IP=20;"
);

// Update reserve error message
guestContent = guestContent.replace(
  "if(e.used+e.pending>=LIMIT || ip.used+ip.pending>=IP_LIMIT)throw failure('访客处理额度已用完，请稍后重试；不要求扫码或手机登录。');",
  "if(e.used+e.pending>=LIMIT || ip.used+ip.pending>=IP_LIMIT)throw failure('未登录游客单日免费额度已用完（每日仅限 3 次，基于设备指纹识别），请前往 Badge 统一登录解锁 10 次及以上额度！', 429);"
);

// Update fingerprint-based owner in access()
const oldOwnerGen = "const owner='guest:'+crypto.createHash('sha256').update(token).digest('hex');\r\n                store.connect(owner,req.ip);req.user={username:owner,role:'guest',guest:true,quota:store.quota(owner)};";
const oldOwnerGenLF = "const owner='guest:'+crypto.createHash('sha256').update(token).digest('hex');\n                store.connect(owner,req.ip);req.user={username:owner,role:'guest',guest:true,quota:store.quota(owner)};";

const newOwnerGen = `const clientFp = req.headers['x-fingerprint'] || req.headers['x-client-fingerprint'] || '';
                const rawFp = clientFp || (req.ip + '|' + (req.headers['user-agent'] || '') + '|' + (req.headers['accept-language'] || ''));
                const fpHash = crypto.createHash('sha256').update(rawFp).digest('hex').slice(0, 32);
                const owner = 'guest:fp_' + fpHash;
                store.connect(owner, req.ip);
                req.user = { username: owner, role: 'guest', guest: true, fingerprint: fpHash, quota: store.quota(owner) };`;

if (guestContent.includes(oldOwnerGen)) {
  guestContent = guestContent.replace(oldOwnerGen, newOwnerGen.replace(/\n/g, '\r\n'));
} else if (guestContent.includes(oldOwnerGenLF)) {
  guestContent = guestContent.replace(oldOwnerGenLF, newOwnerGen);
}

fs.writeFileSync(guestAccessPath, guestContent, 'utf8');
console.log('guest-access.js patched successfully (LIMIT=3, Fingerprint enabled)');

// 2. Update accounts.js (FREE_LIMIT = 10)
const accountsPath = 'D:/Extends/antigravityProj/MediaTools/Media_Tools/lib/accounts.js';
let accountsContent = fs.readFileSync(accountsPath, 'utf8');

accountsContent = accountsContent.replace('const FREE_LIMIT = 20;', 'const FREE_LIMIT = 10;');
accountsContent = accountsContent.replace('FREE_LIMIT: 20', 'FREE_LIMIT: 10');
accountsContent = accountsContent.replace('（20次，按上海时区每日重置）', '（普通用户每日10次，按上海时区每日重置）');

fs.writeFileSync(accountsPath, accountsContent, 'utf8');
console.log('accounts.js patched successfully (FREE_LIMIT=10)');

// 3. Update server.js (/api/download quota check)
const serverPath = 'D:/Extends/antigravityProj/MediaTools/Media_Tools/server.js';
let serverContent = fs.readFileSync(serverPath, 'utf8');

const targetDownloadStart = `app.get('/api/download', mediaAccess, async (req, res) => {
    let targetUrl = req.query.url || req.query.videoUrl;
    const filename = req.query.filename || req.query.title || \`Media_\${Date.now()}\`;
    const customReferer = req.query.referer;

    if (!targetUrl) {
        return res.status(400).send('缺少必需的媒体直链地址 (url)');
    }

    if (targetUrl.startsWith('/api/download') || targetUrl.startsWith('/api/play')) {
        try {
            const parsedUrl = new URL(targetUrl, 'http://127.0.0.1');
            targetUrl = parsedUrl.searchParams.get('url') || targetUrl;
        } catch {}
    }

    try {`;

const newDownloadStart = `app.get('/api/download', mediaAccess, async (req, res) => {
    let targetUrl = req.query.url || req.query.videoUrl;
    const filename = req.query.filename || req.query.title || \`Media_\${Date.now()}\`;
    const customReferer = req.query.referer;

    if (!targetUrl) {
        return res.status(400).send('缺少必需的媒体直链地址 (url)');
    }

    if (targetUrl.startsWith('/api/download') || targetUrl.startsWith('/api/play')) {
        try {
            const parsedUrl = new URL(targetUrl, 'http://127.0.0.1');
            targetUrl = parsedUrl.searchParams.get('url') || targetUrl;
        } catch {}
    }

    // 检查并预占下载额度（游客每日3次，普通用户10次，VIP 100次，SuperVIP 无限）
    let reservation = null;
    try {
        reservation = mediaStore(req.user.username).reserve(req.user.username, 'download');
    } catch (err) {
        const errorMsg = req.user.guest
            ? '【额度限制】未登录访客基于设备指纹每日仅享 3 次免费下载！请点击页面登录通过 Badge 统一登录，立即解锁单日 10 次及更多高级功能。'
            : '【额度限制】今日免费下载与处理额度已用完（普通用户单日 10 次）！升级 VIP 可享 100 次，SuperVIP 享有无限制下载。';
        return res.status(429).send(errorMsg);
    }

    try {`;

const targetDownloadFinish = `        res.on('finish', () => {
            const isTruncated = expectedBytes && bytesReceived < expectedBytes;
            if (res.statusCode < 400 && !streamFailed && !isTruncated) {
                logTransfer(req.user.username, { kind: 'download', status: 'sent', url: targetUrl, title: safeFilename });
            } else if (streamFailed || isTruncated) {
                logTransfer(req.user.username, { kind: 'download', status: 'interrupted', url: targetUrl, title: safeFilename, message: '上游传输中断或未完成' });
            }
        });`;

const newDownloadFinish = `        res.on('finish', () => {
            const isTruncated = expectedBytes && bytesReceived < expectedBytes;
            const success = res.statusCode < 400 && !streamFailed && !isTruncated;
            if (reservation) {
                try { mediaStore(req.user.username).settle(reservation, success); } catch {}
            }
            if (success) {
                logTransfer(req.user.username, { kind: 'download', status: 'sent', url: targetUrl, title: safeFilename });
            } else if (streamFailed || isTruncated) {
                logTransfer(req.user.username, { kind: 'download', status: 'interrupted', url: targetUrl, title: safeFilename, message: '上游传输中断或未完成' });
            }
        });
        res.on('close', () => {
            if (!res.writableEnded && reservation) {
                try { mediaStore(req.user.username).settle(reservation, false); } catch {}
            }
        });`;

if (serverContent.includes(targetDownloadStart.replace(/\n/g, '\r\n'))) {
  serverContent = serverContent.replace(targetDownloadStart.replace(/\n/g, '\r\n'), newDownloadStart.replace(/\n/g, '\r\n'));
} else if (serverContent.includes(targetDownloadStart)) {
  serverContent = serverContent.replace(targetDownloadStart, newDownloadStart);
}

if (serverContent.includes(targetDownloadFinish.replace(/\n/g, '\r\n'))) {
  serverContent = serverContent.replace(targetDownloadFinish.replace(/\n/g, '\r\n'), newDownloadFinish.replace(/\n/g, '\r\n'));
} else if (serverContent.includes(targetDownloadFinish)) {
  serverContent = serverContent.replace(targetDownloadFinish, newDownloadFinish);
}

fs.writeFileSync(serverPath, serverContent, 'utf8');
console.log('server.js patched successfully (Download quota check added)');

