const { chromium } = require('playwright');
const fs=require('fs');
const B='http://localhost:8090', CL='demo-cuentas-claras-2026', OUT='pantallas/', VP={width:1200,height:720};
const cajas={};
async function login(b,u,vp=VP){ const ctx=await b.newContext({viewport:vp,deviceScaleFactor:2}); const p=await ctx.newPage();
  await p.goto(B+'/login'); await p.fill('input[name=usuario]',u); await p.fill('input[name=clave]',CL);
  await Promise.all([p.waitForNavigation(), p.click('main form button[type=submit]')]); return p; }
// Busca un elemento (selector CSS o texto) y sube hasta el contenedor con borde; devuelve % del viewport
async function caja(p, n, sel, texto, subir=true){
  const r = await p.evaluate(([sel,texto,subir])=>{
    let el=null;
    if(sel){ const els=[...document.querySelectorAll(sel)]; if(texto) el=els.find(e=>e.textContent.includes(texto)); else el=els[0]; }
    else { const w=document.createTreeWalker(document.querySelector('main'),NodeFilter.SHOW_TEXT); while(w.nextNode()){ if(w.currentNode.textContent.includes(texto)){ el=w.currentNode.parentElement; break; } } }
    if(!el) return null;
    if(subir){ let e=el; while(e && e.tagName!=='MAIN'){ const cs=getComputedStyle(e); if(parseFloat(cs.borderTopWidth)>0 || parseFloat(cs.borderLeftWidth)>=3 || cs.backgroundColor!=='rgba(0, 0, 0, 0)'){ el=e; break; } e=e.parentElement; } }
    const b=el.getBoundingClientRect(); return {x:b.x/innerWidth*100,y:b.y/innerHeight*100,w:b.width/innerWidth*100,h:b.height/innerHeight*100};
  },[sel,texto,subir]);
  console.log('caja',n,JSON.stringify(r)); if(r) cajas[n]=r; }
async function shot(p,n,url){ if(url) await p.goto(B+url); await p.waitForTimeout(300); await p.screenshot({path:OUT+n+'.png'}); console.log('OK',n); }
(async()=>{ const b=await chromium.launch();
  const c=await login(b,'caja');
  await c.goto(B+'/caja'); const inp='main input:not([type=hidden])'; await c.fill(inp,'quispe');
  await Promise.all([c.waitForLoadState('load'), c.press(inp,'Enter')]); await c.waitForTimeout(400);
  await shot(c,'caja-resultados'); await caja(c,'caja-resultados','main a[href^="/caja/familias/"]',null,false);
  const fam=await c.$('a[href^="/caja/familias/"]'); await Promise.all([c.waitForNavigation(), fam.click()]);
  for(const cb of await c.$$('main input[type=checkbox]')) await cb.check();
  await c.check('main input[type=radio][value=EFECTIVO]');
  await shot(c,'caja-familia');
  const cuotas = await c.evaluate(()=>{ const els=[...document.querySelectorAll('.cuota-opcion')]; const r=els.map(e=>e.getBoundingClientRect());
    const x=Math.min(...r.map(b=>b.x)), y=Math.min(...r.map(b=>b.y)), x2=Math.max(...r.map(b=>b.right)), y2=Math.max(...r.map(b=>b.bottom));
    return {x:x/innerWidth*100,y:y/innerHeight*100,w:(x2-x)/innerWidth*100,h:(y2-y)/innerHeight*100}; }); cajas['caja-familia']=cuotas; console.log('caja caja-familia',JSON.stringify(cuotas));
  await Promise.all([c.waitForLoadState('load'), c.click('main form button[type=submit]:has-text("Revisar")')]); await c.waitForTimeout(400);
  await c.fill('main input[name=recibido]','1000.00');
  await Promise.all([c.waitForLoadState('load'), c.click('main form button[type=submit] >> nth=-1')]); await c.waitForTimeout(600);
  await shot(c,'caja-confirmacion'); await caja(c,'caja-confirmacion','.vuelto',null,false);
  const bol=await c.$('a[href*="/comprobante"]'); await Promise.all([c.waitForNavigation(), bol.click()]);
  await shot(c,'caja-boleta'); await caja(c,'caja-boleta','.comprobante-numero',null,false);
  await shot(c,'caja-cierre','/caja/cierre'); await caja(c,'caja-cierre',null,'Cuenta el efectivo');
  const p=await login(b,'promotor');
  await shot(p,'promotora-inicio','/inicio'); await caja(p,'promotora-inicio','.alerta-critica',null,false);
  await shot(p,'bitacora','/auditoria'); await caja(p,'bitacora',null,'Verificar integridad');
  const d=await login(b,'director'); await shot(d,'aprobaciones','/aprobaciones'); await caja(d,'aprobaciones',null,'Faltante de S/');
  const ad=await login(b,'administracion');
  await shot(ad,'alumnos','/alumnos'); await shot(ad,'importar','/alumnos/importar'); await shot(ad,'pensiones','/pensiones');
  fs.writeFileSync('cajas.json',JSON.stringify(cajas,null,1)); await b.close(); })();
