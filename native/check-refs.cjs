// Depois de colocar os leitores no app, confere que toda função im…/au… chamada no index.html existe.
// (Foi assim que um app saiu com o leitor quebrado: o app chamava uma função que o bloco dos leitores não tinha.)
const fs = require("fs");
const h = fs.readFileSync(process.argv[2] || "www/index.html", "utf8");
const defined = new Set();
for (const m of h.matchAll(/(?:^|[^\w.$])(?:async\s+)?function\s+([A-Za-z_$][\w$]*)/g)) defined.add(m[1]);
for (const m of h.matchAll(/(?:^|[;{}\n,]\s*)(?:var|let|const)\s+([A-Za-z_$][\w$]*)/g)) defined.add(m[1]);
const missing = new Set();
for (const m of h.matchAll(/(?<![\w.$"'`])((?:im|au)[A-Z][A-Za-z0-9]*)\s*\(/g)) if (!defined.has(m[1])) missing.add(m[1]);
if (missing.size) { console.error("Funções chamadas e não definidas: " + [...missing].join(", ")); process.exit(1); }
console.log("Referências dos leitores conferidas.");
