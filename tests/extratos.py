"""Bateria dos extratos: compara o leitor genérico com o leitor próprio de cada banco e com o saldo.
Uso: python3 -I tests/extratos.py [pasta com os PDFs]   (os PDFs reais não ficam no repositório)"""
import base64,sys,os,json,glob,shutil
from collections import Counter
from playwright.sync_api import sync_playwright
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
args=[a for a in sys.argv[1:] if not a.startswith('-')];FIX=args[0] if args else '/home/claude/fixtures'
LIB='/opt/npm-tools/node_modules/pdfjs-dist/legacy/build/'
os.makedirs(ROOT+'/pdf',exist_ok=True)
shutil.copy(LIB+'pdf.min.mjs',ROOT+'/pdf/pdf.min.js');shutil.copy(LIB+'pdf.worker.min.mjs',ROOT+'/pdf/pdf.worker.min.js')
import subprocess
srv=subprocess.Popen(['python3','-m','http.server','8799','-d',ROOT],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
import time;time.sleep(1)
p=sync_playwright().start();b=p.chromium.launch();pg=b.new_context().new_page();errs=[]
pg.on('pageerror',lambda e:errs.append(str(e)))
pg.add_init_script("localStorage.setItem('lang','pt-BR');localStorage.setItem('tutdone','1')")
pg.goto('http://localhost:8799/index.html?nosplash');pg.wait_for_timeout(1500)
JS="""async d=>{var u=Uint8Array.from(atob(d),c=>c.charCodeAt(0));var f=new File([u],'x.pdf',{type:'application/pdf'});
var PG=await imPdfPages(u.buffer),L=imPdfLines(PG),res={};
try{var r=await imRead(f);res.ded={n:r.tx.length,label:r.label,exp:r.exp,tx:r.tx.map(x=>[x.date,Math.round(x.amt*100),(x.desc||'')])}}catch(e){res.ded={err:String(e)}}
try{var g=imGenericBest(PG,L);res.gen={n:g.length,tx:g.map(x=>[x.date,Math.round(x.amt*100),(x.desc||''),x.bal==null?null:x.bal]),bc:imBalCheck(g),cur:(g[0]||{}).cur}}catch(e){res.gen={err:String(e)}}
try{var bb=imBuild(await imRead(f));res.build={items:bb.items.length,olds:bb.olds.length,total:bb.total}}catch(e){res.build={err:String(e)}}
try{var c=imColPdf(PG);res.col={n:c.length,bc:imBalCheck(c).st,tx:c.map(x=>[x.date,Math.round(x.amt*100),x.desc,x.bal==null?null:x.bal])}}catch(e){res.col={err:String(e),st:e.stack}}
return res}"""
tot=[0,0]
def gold_revolut(path):
    import re,subprocess
    from datetime import datetime
    t=subprocess.run(['pdftotext','-layout',path,'-'],capture_output=True,text=True).stdout
    mo={m:i+1 for i,m in enumerate('Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec'.split())}
    ob=[float(x.replace(',','')) for x in re.findall(r'\u20ac([\d,]+\.\d\d)',t[:t.index('Account transactions')])[:1]]
    prev=34.50;out=[];sec=0
    for l in t.splitlines():
        if l.startswith('Deposit transactions from'): prev=100.00;sec=1
        m=re.match(r'^\s*([A-Z][a-z]{2}) (\d{1,2}), (\d{4})\s+(.*)$',l)
        if not m: continue
        am=re.findall(r'\u20ac([\d,]+\.\d\d)',m[4])
        if len(am)<2: continue
        a=float(am[0].replace(',',''));b=float(am[-1].replace(',',''))
        d=round(b-prev,2)
        if abs(abs(d)-a)>0.005: print('   gabarito: salto em',l[:60]); prev=b; continue
        prev=b
        if abs(d)>=0.005: out.append(('%04d-%02d-%02d'%(int(m[3]),mo[m[1]],int(m[2])),int(round(d*100))))
    return out
for f in sorted(glob.glob(FIX+'/*.pdf')):
    d=base64.b64encode(open(f,'rb').read()).decode();r=pg.evaluate(JS,d);n=os.path.basename(f)
    ded,gen=r['ded'],r['gen']
    if n.startswith('revolut'):
        g=gold_revolut(f);c2=Counter((t[0],t[1]) for t in gen['tx']) if 'tx' in gen else Counter()
        c1=Counter(g);falta=sum((c1-c2).values());sobra=sum((c2-c1).values());ok=falta==0 and sobra==0
        print(('OK   ' if ok else 'FALHA'),n.ljust(14),'gabarito=%d'%len(g),'genérico=%d'%gen.get('n',0),'faltam=%d sobram=%d'%(falta,sobra),'saldo=%s'%gen['bc'].get('st'),'moeda=%s'%gen.get('cur'),'| gabarito pelo saldo (o leitor próprio atual não lê este formato)')
        tot[0 if ok else 1]+=1
        if '-v' in sys.argv and not ok:
            for k in list(c1-c2)[:6]: print('   falta',k)
            for k in list(c2-c1)[:6]: print('   sobra',k)
        continue
    if 'err' in gen or 'err' in ded: print('FALHA',n,ded.get('err'),gen.get('err'));tot[1]+=1;continue
    c1=Counter((t[0],t[1]) for t in ded['tx']);c2=Counter((t[0],t[1]) for t in gen['tx'])
    falta=sum((c1-c2).values());sobra=sum((c2-c1).values())
    bd=r.get('build',{})
    if 'err' in bd: print('FALHA imBuild',n,bd['err']);tot[1]+=1
    col=r['col']; 
    if '-c' in sys.argv: print('   colunas:',col.get('n'),col.get('bc'),col.get('err'),col.get('st'))
    ok=falta==0 and sobra==0
    print(('OK   ' if ok else 'FALHA'),n.ljust(14),'próprio=%d'%ded['n'],'genérico=%d'%gen['n'],'faltam=%d sobram=%d'%(falta,sobra),'saldo=%s'%gen['bc'].get('st'),'moeda=%s'%gen['cur'],'| %s'%ded.get('label'))
    tot[0 if ok else 1]+=1
    if '-v' in sys.argv and not ok:
        for k in list((c1-c2))[:6]: print('   falta',k)
        for k in list((c2-c1))[:6]: print('   sobra',k)
print('ok=%d falha=%d'%tuple(tot),'erros de página:',errs)
b.close();p.stop();srv.terminate();shutil.rmtree(ROOT+'/pdf')
