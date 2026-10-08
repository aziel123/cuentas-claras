const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.addInitScript(c => { window.CAJAS = c; }, JSON.parse(fs.readFileSync('cajas.json', 'utf8')));
  await p.goto('file://' + path.resolve('escenas/animado.html'));
  const ev = await p.evaluate(() => [...document.querySelectorAll('.escena')].map(sc => { const D = +sc.dataset.dur;
    const t = el => (el.dataset.t !== undefined ? +el.dataset.t : (el.dataset.tf !== undefined ? +el.dataset.tf * D : 0)) + (+el.dataset.dl || 0);
    const items = [...sc.querySelectorAll('[data-a]')].map(el => ({ a: el.dataset.a, t: Math.round(t(el)), cls: (el.getAttribute('class') || '') + ' ' + el.tagName.toLowerCase(),
      press: el.dataset.press !== undefined ? +el.dataset.press : (el.dataset.pressf !== undefined ? Math.round(+el.dataset.pressf * D) : null),
      fin: el.dataset.fin !== undefined ? +el.dataset.fin : null }));
    return { id: sc.id, D, items }; }));
  fs.writeFileSync('eventos.json', JSON.stringify(ev, null, 1)); console.log(ev.length, ev.reduce((s, e) => s + e.items.length, 0)); await b.close(); })();
