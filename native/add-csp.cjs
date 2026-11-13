// Acrescenta uma política de segurança (CSP) à página do app Android.
// Só o app a recebe: bloqueia conexões, scripts, imagens e frames de qualquer endereço
// que não seja o próprio app (e as cotações: api.frankfurter.dev, data-api.binance.vision e fapi.binance.com).
const fs = require("fs");
const crypto = require("crypto");
const f = process.argv[2] || "www/index.html";
let h = fs.readFileSync(f, "utf8");
if (h.includes("Content-Security-Policy")) process.exit(0);
// Scripts: só os blocos <script> do próprio app (por hash). Sem 'unsafe-inline' para scripts,
// um texto malicioso que virasse HTML não consegue executar código. Se sobrar algum
// manipulador inline (onclick=...), volta ao modo antigo para não quebrar o app.
const blocks = [...h.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map((m) => m[1]);
const hasInline = /\son[a-z]+\s*=\s*["']/i.test(h) || /javascript:/i.test(h);
const hashes = blocks.map((b) => "'sha256-" + crypto.createHash("sha256").update(b, "utf8").digest("base64") + "'");
const scriptSrc = hasInline || !hashes.length
  ? "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval' blob:"
  : "script-src 'self' " + hashes.join(" ") + " 'wasm-unsafe-eval' blob:";
console.log(hasInline ? "AVISO: manipulador inline encontrado, script-src com unsafe-inline" : "script-src por hash (" + hashes.length + " blocos)");
const csp = [
  "default-src 'self'",
  scriptSrc,
  "worker-src 'self' blob:",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self' blob: data: https://docs.google.com https://api.frankfurter.dev https://data-api.binance.vision https://fapi.binance.com",
  "media-src 'none'",
  "object-src 'none'",
  "frame-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
].join("; ");
const tag = `<meta http-equiv="Content-Security-Policy" content="${csp}">`;
if (!/<meta charset="utf-8">/i.test(h)) { console.error("meta charset não encontrada"); process.exit(1); }
h = h.replace(/<meta charset="utf-8">/i, (m) => m + "\n" + tag);
fs.writeFileSync(f, h);
console.log("CSP aplicada em", f);
