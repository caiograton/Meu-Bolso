// Coloca o dist/leitores.js do repositório sigillum-leitores no lugar do bloco LEITORES do app.
const fs=require("fs");const [,,page,dist]=process.argv;
const h=fs.readFileSync(page,"utf8"),d=fs.readFileSync(dist,"utf8").trim();
const a=h.indexOf("/*LEITORES:INI*/"),b=h.indexOf("/*LEITORES:FIM*/");
if(a<0||b<a||h.indexOf("/*LEITORES:INI*/",a+1)>=0){console.error("Bloco LEITORES não encontrado no app");process.exit(1)}
if(!d.startsWith("/*LEITORES:INI*/")||!d.endsWith("/*LEITORES:FIM*/")){console.error("dist/leitores.js incompleto");process.exit(1)}
fs.writeFileSync(page,h.slice(0,a)+d+h.slice(b+"/*LEITORES:FIM*/".length));
console.log("Leitores aplicados: "+((d.match(/LEITORES_V="([^"]+)"/)||[])[1]||"?"));
