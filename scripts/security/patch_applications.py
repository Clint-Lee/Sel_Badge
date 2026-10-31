#!/usr/bin/env python3
"""Apply bounded security changes to the existing application sources."""
import argparse
import os
from pathlib import Path
import subprocess


def replace_once(text, old, new):
    if new in text:
        return text
    if old not in text:
        raise RuntimeError("Expected source anchor is missing; refusing an unreviewed patch")
    return text.replace(old, new, 1)


def commit(path, text):
    subprocess.run(["node", "--check"], input=text, text=True, check=True)
    temporary = path.with_name("." + path.name + ".security-new")
    temporary.write_text(text, encoding="utf-8")
    os.replace(temporary, path)


def xiyou(root):
    path = root / "server.js"
    text = path.read_text(encoding="utf-8")
    text = replace_once(text, "const PORT = Number(process.env.PORT) || 5173;", "const PORT = Number(process.env.PORT) || 5173;\nconst HOST = process.env.HOST || '127.0.0.1';")
    text = replace_once(text, "const SAVE_DIR = path.join(ROOT_DIR, 'saves');", "const SAVE_DIR = path.resolve(process.env.XIYOU_SAVE_DIR || path.join(ROOT_DIR, 'saves'));\nconst COOKIE_SECURITY = PUBLIC_URL.startsWith('https://') ? '; Secure' : '';\nconst PUBLIC_ASSET_DIRECTORIES = new Set(['assets', 'css', 'js', 'rules']);")
    text = text.replace("SameSite=Lax; Path=/;", "SameSite=Lax${COOKIE_SECURITY}; Path=/;")
    text = replace_once(text, "try { return new URL(origin).host === req.headers.host; } catch { return false; }", "try { return new URL(origin).origin === new URL(PUBLIC_URL).origin; } catch { return false; }")
    anchor = "      if (reqPath === '/' || reqPath === '') reqPath = '/index.html';"
    guard = """
      reqPath = path.posix.normalize(reqPath);
      const segments = reqPath.split('/').filter(Boolean);
      const publicPath = reqPath === '/index.html' || PUBLIC_ASSET_DIRECTORIES.has(segments[0]);
      if (!publicPath || reqPath.includes('\\\\') || reqPath.includes('\\0') || segments.some(segment => segment.startsWith('.'))) {
        res.writeHead(404); res.end(); return;
      }
      if (!['GET', 'HEAD'].includes(req.method)) {
        res.writeHead(405, { Allow: 'GET, HEAD' }); res.end(); return;
      }
"""
    if "const publicPath =" not in text:
        text = replace_once(text, anchor, anchor + guard)
    text = replace_once(text, "if (!filePath.startsWith(ROOT_DIR)) {", "if (!filePath.startsWith(ROOT_DIR + path.sep)) {")
    anchor = "        res.writeHead(200, {\n          'Content-Type': getContentType(filePath),"
    check = """        // Reject symlinks that escape the public repository boundary.
        const realPath = fs.realpathSync(filePath);
        if (!realPath.startsWith(ROOT_DIR + path.sep)) { res.writeHead(404); res.end(); return; }
"""
    if "const realPath =" not in text:
        text = replace_once(text, anchor, check + anchor)
    text = replace_once(text, "  server.listen(port, () => {", "  server.listen(port, HOST, () => {")
    text = replace_once(text, "    if (err.code === 'EADDRINUSE') {", "    if (err.code === 'EADDRINUSE' && process.env.NODE_ENV === 'production') {\n      console.error('Configured production port is occupied'); process.exitCode = 1; return;\n    }\n    if (err.code === 'EADDRINUSE') {")
    commit(path, text)


def media(root):
    path = root / "server.js"
    text = path.read_text(encoding="utf-8")
    text = replace_once(text, "const app = express();", "const app = express();\n// Only the local reverse proxy may supply client IP and HTTPS state.\napp.set('trust proxy', 'loopback');")
    text = replace_once(text, "httpOnly: true, sameSite: 'lax', maxAge: 10 * 60 * 1000, path: '/'", "httpOnly: true, sameSite: 'lax', secure: req.secure, maxAge: 10 * 60 * 1000, path: '/'")
    text = replace_once(text, "res.clearCookie('hotpot_badge_state', { path: '/' });", "res.clearCookie('hotpot_badge_state', { path: '/', httpOnly: true, sameSite: 'lax', secure: req.secure });")
    commit(path, text)
    path = root / "lib/accounts.js"
    text = path.read_text(encoding="utf-8")
    old = "req.headers.origin && new URL(req.headers.origin).host !== req.headers.host"
    new = "req.headers.origin && new URL(req.headers.origin).origin !== new URL(process.env.HOTPOT_PUBLIC_URL || (req.protocol + '://' + req.headers.host)).origin"
    text = replace_once(text, old, new)
    commit(path, text)
    guest(root)


def guest(root):
    path = root / "lib/guest-access.js"
    text = path.read_text(encoding="utf-8")
    if "const SIGNING_KEY" in text:
        return
    text = replace_once(text, "const crypto=require('crypto');", """const crypto=require('crypto');
const fs=require('fs'),path=require('path');
const SIGNING_KEY=process.env.HOTPOT_GUEST_SECRET || (process.env.NODE_ENV==='production' ? '' : crypto.randomBytes(48).toString('hex'));
if(SIGNING_KEY.length<32)throw new Error('HOTPOT_GUEST_SECRET must contain at least 32 characters');
const sign=value=>crypto.createHmac('sha256',SIGNING_KEY).update(value).digest('hex');
function validToken(token) {
    if(typeof token!=='string' || !/^\\d{13}\\.[a-f0-9]{64}\\.[a-f0-9]{64}$/.test(token))return false;
    const [issued,nonce,signature]=token.split('.'),age=Date.now()-Number(issued);
    return age>=-300000 && age<30*86400000 && crypto.timingSafeEqual(Buffer.from(signature,'hex'),Buffer.from(sign(issued+'.'+nonce),'hex'));
}""")
    text = replace_once(text, "TTL=8*3600000", "TTL=48*3600000")
    text = replace_once(text, "function createStore() {", "function createStore(filename) {")
    anchor = "    function entry(owner)"
    persistence = """    if(filename && fs.existsSync(filename)) {
        const saved=JSON.parse(fs.readFileSync(filename,'utf8'));
        if(saved.version!==1)throw new Error('Unsupported visitor ledger version');
        for(const [key,value] of saved.visitors)if(value.date===day())visitors.set(key,{...value,pending:0});
        for(const [key,value] of saved.ips)if(value.date===day())ips.set(key,{...value,pending:0});
    }
    function persist() {
        if(!filename)return;
        fs.mkdirSync(path.dirname(filename),{recursive:true,mode:0o700});
        const rows=map=>[...map].filter(([,value])=>value.date===day()).map(([key,value])=>[key,{...value,used:value.used+value.pending,pending:0}]);
        const temporary=filename+'.tmp';
        fs.writeFileSync(temporary,JSON.stringify({version:1,visitors:rows(visitors),ips:rows(ips)}),{mode:0o600});
        fs.renameSync(temporary,filename);
    }
"""
    text = replace_once(text, anchor, persistence + anchor)
    text = replace_once(text, "reservations.set(id,{e,ip,action,owner});return id;", "reservations.set(id,{e,ip,action,owner});persist();return id;")
    text = replace_once(text, "else activeOwners.delete(r.owner);}", "else activeOwners.delete(r.owner);persist();}")
    text = replace_once(text, "const store=createStore();", "const store=createStore(process.env.HOTPOT_GUEST_LEDGER || (process.env.NODE_ENV==='production' ? path.join(__dirname,'../.runtime/data/guest-quotas.json') : undefined));")
    start = text.index("                const match=token?.match(")
    end = text.index("            }\n            res.setHeader", start)
    text = text[:start] + """                if(!validToken(token)) {
                    const value=Date.now()+'.'+crypto.randomBytes(32).toString('hex');
                    token=value+'.'+sign(value);
                    res.cookie(COOKIE,token,{httpOnly:true,sameSite:'strict',secure:req.secure,path:'/',maxAge:30*86400000});
                }
                // Client supplied fingerprints never grant access to another visitor's private work.
                const owner='guest:'+crypto.createHash('sha256').update(token).digest('hex');
                store.connect(owner,req.ip);req.user={username:owner,role:'guest',guest:true,quota:store.quota(owner)};
""" + text[end:]
    text = text.replace('（每日仅限 3 次，基于设备指纹识别）', '（每个访客每日限 3 次，并设有同 IP 总额度）')
    commit(path, text)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--xiyou", type=Path)
    parser.add_argument("--media", type=Path)
    args = parser.parse_args()
    if args.xiyou:
        xiyou(args.xiyou)
    if args.media:
        media(args.media)
    print("Application security patches applied and syntax checked")
