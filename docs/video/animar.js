const { chromium } = require('playwright');
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path');
const FPS = 30; const html = 'file://' + path.resolve('escenas/animado.html');
const cajas = JSON.parse(fs.readFileSync('cajas.json', 'utf8'));
const [, , modo, arg] = process.argv; // modo: "cuadros id t1,t2,..." | "video workers"
async function pagina(b) { const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.addInitScript(c => { window.CAJAS = c; }, cajas); await p.goto(html); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300); return p; }
(async () => {
  const b = await chromium.launch();
  if (modo === 'cuadros') { const p = await pagina(b); const [id, ts] = arg.split(':');
    for (const t of ts.split(',')) { await p.evaluate(([id, t]) => render(id, +t), [id, t]); await p.screenshot({ path: `prueba-${id}-${t}.png` }); }
    await b.close(); return; }
  const p0 = await pagina(b); const escenas = await p0.evaluate(() => escenas()); await p0.close();
  fs.mkdirSync('clips2', { recursive: true }); fs.writeFileSync('escenas2.json', JSON.stringify(escenas));
  const cola = escenas.map((e, i) => ({ ...e, i })).filter(e => !process.env.SOLO || process.env.SOLO.split(',').includes(e.id));
  const W = 3; let siguiente = 0; const t0 = Date.now();
  async function trabajador() { const p = await pagina(b);
    while (siguiente < cola.length) { const e = cola[siguiente++]; const n = Math.round(e.dur / 1000 * FPS);
      const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS), `clips2/${String(e.i + 1).padStart(2, '0')}.mp4`]);
      for (let f = 0; f < n; f++) { await p.evaluate(([id, t]) => render(id, t), [e.id, f * 1000 / FPS]);
        const buf = await p.screenshot({ type: 'jpeg', quality: 92 }); if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r)); }
      ff.stdin.end(); await new Promise(r => ff.on('close', r)); console.log(e.id, n, 'cuadros', Math.round((Date.now() - t0) / 1000) + 's'); }
    await p.close(); }
  await Promise.all(Array.from({ length: W }, trabajador)); await b.close();
})();
