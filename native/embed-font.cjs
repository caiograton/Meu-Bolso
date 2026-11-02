// Embute a fonte Bricolage Grotesque (latin, pesos 400/600/800) dentro da página do app
// e remove os links para o servidor do Google. Roda na montagem do APK (www/index.html).
const fs = require("fs");
const path = require("path");
const file = "www/index.html";
const dir = path.join("node_modules", "@fontsource", "bricolage-grotesque", "files");
let html = fs.readFileSync(file, "utf8");
if (!fs.existsSync(dir)) throw new Error("Pacote da fonte não encontrado em " + dir);
let css = "";
for (const w of [400, 600, 800]) {
  const name = fs.readdirSync(dir).find((n) => new RegExp("-latin-" + w + "-normal\\.woff2$").test(n) && !/ext/.test(n));
  if (!name) throw new Error("Arquivo da fonte (peso " + w + ") não encontrado");
  const b64 = fs.readFileSync(path.join(dir, name)).toString("base64");
  css += "@font-face{font-family:'Bricolage Grotesque';font-style:normal;font-weight:" + w +
    ";font-display:swap;src:url(data:font/woff2;base64," + b64 + ") format('woff2')}";
}
const before = html.length;
html = html.replace(/<link[^>]*fonts\.googleapis\.com[^>]*>\s*/g, "").replace(/<link[^>]*fonts\.gstatic\.com[^>]*>\s*/g, "");
if (/fonts\.g(oogleapis|static)\.com/.test(html)) throw new Error("Ainda há links para fontes do Google");
html = html.replace("</head>", "<style>" + css + "</style>\n</head>");
fs.writeFileSync(file, html);
console.log("Fonte embutida: +" + Math.round((html.length - before) / 1024) + " KB");
