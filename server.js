const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 3000;
const HOST = '0.0.0.0';
const DB_PATH = path.join(__dirname, 'data', 'db.json');
const PUBLIC_DIR = path.join(__dirname, 'public');
const TOKEN_SECRET = process.env.JWT_SECRET || 'degistir-bunu-guclu-sifre-yap';

function ensureDb() {
  const dir = path.dirname(DB_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  if (!fs.existsSync(DB_PATH)) {
    fs.writeFileSync(DB_PATH, JSON.stringify({ users: [], notes: [] }, null, 2));
  }
}

function readDb() {
  ensureDb();
  return JSON.parse(fs.readFileSync(DB_PATH, 'utf-8'));
}

function writeDb(db) {
  fs.writeFileSync(DB_PATH, JSON.stringify(db, null, 2));
}

function send(res, code, data, headers = {}) {
  const body = typeof data === 'string' ? data : JSON.stringify(data);
  res.writeHead(code, {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
    ...headers
  });
  res.end(body);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) req.destroy();
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error('Geçersiz JSON'));
      }
    });
    req.on('error', reject);
  });
}

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.pbkdf2Sync(password, salt, 100000, 64, 'sha512').toString('hex');
  return `${salt}:${hash}`;
}

function verifyPassword(password, stored) {
  const [salt, originalHash] = stored.split(':');
  const hash = crypto.pbkdf2Sync(password, salt, 100000, 64, 'sha512').toString('hex');
  return crypto.timingSafeEqual(Buffer.from(hash), Buffer.from(originalHash));
}

function createToken(userId) {
  const payload = JSON.stringify({ userId, exp: Date.now() + 1000 * 60 * 60 * 24 * 30 });
  const base = Buffer.from(payload).toString('base64url');
  const sig = crypto.createHmac('sha256', TOKEN_SECRET).update(base).digest('base64url');
  return `${base}.${sig}`;
}

function verifyToken(token) {
  const [base, sig] = String(token || '').split('.');
  if (!base || !sig) return null;
  const expected = crypto.createHmac('sha256', TOKEN_SECRET).update(base).digest('base64url');
  if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null;
  const payload = JSON.parse(Buffer.from(base, 'base64url').toString('utf-8'));
  if (payload.exp < Date.now()) return null;
  return payload.userId;
}

function id() {
  return crypto.randomBytes(12).toString('hex');
}

function contentType(file) {
  if (file.endsWith('.html')) return 'text/html; charset=utf-8';
  if (file.endsWith('.css')) return 'text/css; charset=utf-8';
  if (file.endsWith('.js')) return 'text/javascript; charset=utf-8';
  return 'text/plain; charset=utf-8';
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, '');

  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === 'GET' && url.pathname === '/health') return send(res, 200, { ok: true });

  if (req.method === 'POST' && url.pathname === '/api/register') {
    try {
      const { email, password } = await readJson(req);
      if (!email || !password || String(password).length < 6) {
        return send(res, 400, { error: 'Geçerli email ve min 6 karakter şifre gir.' });
      }
      const db = readDb();
      if (db.users.some((u) => u.email.toLowerCase() === String(email).toLowerCase())) {
        return send(res, 409, { error: 'Bu email zaten kayıtlı.' });
      }
      db.users.push({ id: id(), email: String(email), passwordHash: hashPassword(password), createdAt: new Date().toISOString() });
      writeDb(db);
      return send(res, 201, { message: 'Kayıt başarılı.' });
    } catch {
      return send(res, 400, { error: 'Hatalı istek.' });
    }
  }

  if (req.method === 'POST' && url.pathname === '/api/login') {
    try {
      const { email, password } = await readJson(req);
      const db = readDb();
      const user = db.users.find((u) => u.email.toLowerCase() === String(email || '').toLowerCase());
      if (!user || !verifyPassword(String(password || ''), user.passwordHash)) {
        return send(res, 401, { error: 'Email ya da şifre hatalı.' });
      }
      return send(res, 200, { token: createToken(user.id), email: user.email });
    } catch {
      return send(res, 400, { error: 'Hatalı istek.' });
    }
  }

  if (url.pathname.startsWith('/api/notes')) {
    const token = (req.headers.authorization || '').replace('Bearer ', '');
    const userId = verifyToken(token);
    if (!userId) return send(res, 401, { error: 'Token gerekli/geçersiz.' });

    if (req.method === 'GET' && url.pathname === '/api/notes') {
      const db = readDb();
      const notes = db.notes.filter((n) => n.userId === userId).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
      return send(res, 200, notes);
    }

    if (req.method === 'POST' && url.pathname === '/api/notes') {
      try {
        const { title, content } = await readJson(req);
        if (!title && !content) return send(res, 400, { error: 'Not boş olamaz.' });
        const db = readDb();
        const now = new Date().toISOString();
        const note = { id: id(), userId, title: title || 'Başlıksız not', content: content || '', createdAt: now, updatedAt: now };
        db.notes.push(note);
        writeDb(db);
        return send(res, 201, note);
      } catch {
        return send(res, 400, { error: 'Hatalı istek.' });
      }
    }

    const noteId = url.pathname.split('/').pop();
    if (req.method === 'PUT') {
      try {
        const { title, content } = await readJson(req);
        const db = readDb();
        const note = db.notes.find((n) => n.id === noteId && n.userId === userId);
        if (!note) return send(res, 404, { error: 'Not bulunamadı.' });
        note.title = title || 'Başlıksız not';
        note.content = content || '';
        note.updatedAt = new Date().toISOString();
        writeDb(db);
        return send(res, 200, note);
      } catch {
        return send(res, 400, { error: 'Hatalı istek.' });
      }
    }

    if (req.method === 'DELETE') {
      const db = readDb();
      const before = db.notes.length;
      db.notes = db.notes.filter((n) => !(n.id === noteId && n.userId === userId));
      if (db.notes.length === before) return send(res, 404, { error: 'Not bulunamadı.' });
      writeDb(db);
      return send(res, 200, { message: 'Not silindi.' });
    }
  }

  // Static files
  let filePath = path.join(PUBLIC_DIR, url.pathname === '/' ? 'index.html' : url.pathname);
  if (!filePath.startsWith(PUBLIC_DIR)) return send(res, 403, 'Forbidden');

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    return send(res, 200, fs.readFileSync(filePath), { 'Content-Type': contentType(filePath) });
  }

  return send(res, 404, { error: 'Bulunamadı' });
});

ensureDb();
server.listen(PORT, HOST, () => {
  console.log(`Uygulama çalışıyor: http://${HOST}:${PORT}`);
});
