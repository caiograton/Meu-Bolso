"""Teste de banco 'escondido': extratos sintéticos (dados inventados) com layouts que o motor nunca viu.
Uso: python3 -I tests/sinteticos.py   (gera os PDFs em /home/claude/fixtures_syn com gen.py)"""
import base64,sys,os,glob,shutil,subprocess,time,ast
from collections import Counter
from playwright.sync_api import sync_playwright
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)));SYN='/home/claude/fixtures_syn'
LIB='/opt/npm-tools/node_modules/pdfjs-dist/legacy/build/'
os.makedirs(ROOT+'/pdf',exist_ok=True);shutil.copy(LIB+'pdf.min.mjs',ROOT+'/pdf/pdf.min.js');shutil.copy(LIB+'pdf.worker.min.mjs',ROOT+'/pdf/pdf.worker.min.js')
srv=subprocess.Popen(['python3','-m','http.server','8797','-d',ROOT],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);time.sleep(1)
p=sync_playwright().start();b=p.chromium.launch();pg=b.new_context().new_page();errs=[]
pg.on('pageerror',lambda e:errs.append(str(e)))
pg.add_init_script("localStorage.setItem('lang','pt-BR');localStorage.setItem('tutdone','1')")
pg.goto('http://localhost:8797/index.html?nosplash');pg.wait_for_timeout(1500)
gold=Counter(ast.literal_eval(open(SYN+'/caixa_like.json').read()))
JS="""async d=>{var u=Uint8Array.from(atob(d),c=>c.charCodeAt(0));var f=new File([u],'x.pdf',{type:'application/pdf'});var r=await imRead(f);return {n:r.tx.length,exp:r.exp,bc:imBalCheck(r.tx).st,tx:r.tx.map(x=>[x.date,Math.round(x.amt*100)])}}"""
ok=bad=0
for f in sorted(glob.glob(SYN+'/*.pdf')):
    try: r=pg.evaluate(JS,base64.b64encode(open(f,'rb').read()).decode())
    except Exception as e: print('FALHA',os.path.basename(f),str(e)[:80]);bad+=1;continue
    c=Counter((t[0],t[1]) for t in r['tx']);fa=sum((gold-c).values());so=sum((c-gold).values())
    good=fa==0 and so==0;ok+=good;bad+=not good
    print('OK   ' if good else 'FALHA',os.path.basename(f).ljust(16),'linhas=%d/%d'%(r['n'],sum(gold.values())),'faltam=%d sobram=%d'%(fa,so),'saldo=%s'%r['bc'],'experimental=%s'%r['exp'])
print('ok=%d falha=%d'%(ok,bad),errs);b.close();p.stop();srv.terminate();shutil.rmtree(ROOT+'/pdf')
