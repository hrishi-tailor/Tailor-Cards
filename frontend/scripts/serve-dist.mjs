// Serves a production build (dist) with the exact headers from frontend/vercel.json, so the security headers can be
// checked locally and scanned in CI (ZAP baseline). Usage: node scripts/serve-dist.mjs dist vercel.json 4173
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
const [,, root, configPath, port] = process.argv;
const headers = JSON.parse(await readFile(configPath, 'utf8')).headers[0].headers;
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.ico': 'image/x-icon' };
createServer(async (req, res) => {
  const path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
  let file = join(root, path);
  let body;
  try { body = await readFile(file); } catch { file = join(root, 'index.html'); body = await readFile(file); }
  for (const h of headers) res.setHeader(h.key, h.value);
  res.setHeader('Content-Type', types[extname(file)] || 'application/octet-stream');
  res.end(body);
}).listen(Number(port), () => console.log('serving on', port));
