"""Gera extratos sintéticos (dados inventados, layouts parecidos com Caixa, BB e banco digital) em /home/claude/fixtures_syn."""
import random,os
os.makedirs("/home/claude/fixtures_syn",exist_ok=True);os.chdir("/home/claude/fixtures_syn")
from playwright.sync_api import sync_playwright
random.seed(7)
def money(v):
    s=f"{abs(v):,.2f}".replace(",","X").replace(".",",").replace("X",".");return s
def rows_data(n,start_bal,days=20):
    out=[];bal=start_bal;d=1
    names=["COMPRA CARTAO DEBITO","PIX RECEBIDO","PIX ENVIADO","TARIFA CESTA","PAGTO BOLETO","SAQUE ATM","TED RECEBIDA","DEB AUTOMATICO ENERGIA"]
    for i in range(n):
        d=min(28,d+random.choice([0,0,1,1,2]));a=round(random.uniform(5,900),2);neg=random.random()<.6
        v=-a if neg else a;bal=round(bal+v,2);out.append((d,random.choice(names),v,bal))
    return out
def pdf(name,html):
    with sync_playwright() as p:
        b=p.chromium.launch();pg=b.new_page();pg.set_content(html);pg.pdf(path=name,format="A4",margin={'top':'20mm','bottom':'20mm','left':'12mm','right':'12mm'});b.close()
css="<style>body{font-family:Arial;font-size:11px}table{width:100%;border-collapse:collapse}td,th{padding:3px 4px}.r{text-align:right}</style>"
D=rows_data(70,1500.00)
# Caixa-like: Data Mov. | Nr. Doc. | Histórico | Valor (C/D) | Saldo (C/D)
h="<html>"+css+"<body><h3>CAIXA ECONÔMICA FEDERAL - Extrato por período</h3><p>Conta: 1234-5 Ag 0001</p><table><tr><th>Data Mov.</th><th>Nr. Doc.</th><th>Histórico</th><th class=r>Valor</th><th class=r>Saldo</th></tr>"
for d,n,v,b in D:
    h+=f"<tr><td>{d:02d}/10/2026</td><td>{random.randint(100000,999999)}</td><td>{n}</td><td class=r>{money(v)} {'C' if v>0 else 'D'}</td><td class=r>{money(b)} {'C' if b>=0 else 'D'}</td></tr>"
h+="</table><p>Saldo final R$ 0,00 - Ouvidoria 0800 725 7474</p></body></html>"
pdf("caixa_like.pdf",h)
open("caixa_like.json","w").write(str([(f"2026-10-{d:02d}",int(round(v*100))) for d,n,v,b in D]))
# BB-like: sem coluna de saldo, valor com C/D
h="<html>"+css+"<body><h3>Banco do Brasil - Extrato de conta corrente</h3><table><tr><th>Dt. movimento</th><th>Histórico</th><th>Documento</th><th class=r>Valor R$</th></tr>"
for d,n,v,b in D:
    h+=f"<tr><td>{d:02d}/10/2026</td><td>{n}</td><td>{random.randint(1000,99999)}</td><td class=r>{money(v)} {'C' if v>0 else 'D'}</td></tr>"
h+="</table></body></html>"
pdf("bb_like.pdf",h)
# Nubank/neobank-like: débito e crédito em colunas separadas, data por extenso
h="<html>"+css+"<body><h3>Extrato</h3><table><tr><th>Data</th><th>Descrição</th><th class=r>Débito</th><th class=r>Crédito</th><th class=r>Saldo</th></tr>"
for d,n,v,b in D:
    h+=f"<tr><td>{d:02d} OUT 2026</td><td>{n}</td><td class=r>{money(v) if v<0 else ''}</td><td class=r>{money(v) if v>0 else ''}</td><td class=r>{money(b)}</td></tr>"
h+="</table></body></html>"
pdf("neo_like.pdf",h)
