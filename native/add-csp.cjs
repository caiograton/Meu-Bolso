// Acrescenta uma política de segurança (CSP) à página do app Android.
// Só o app a recebe: bloqueia conexões, scripts, imagens e frames de qualquer endereço
// que não seja o próprio app (e a cotação, em api.frankfurter.dev).
const fs = require("fs");
const f = process.argv[2] || "www/index.html";
let h = fs.readFileSync(f, "utf8");
if (h.includes("Content-Security-Policy")) process.exit(0);
const csp = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval' blob:",
  "worker-src 'self' blob:",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self' blob: data: https://api.frankfurter.dev",
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
