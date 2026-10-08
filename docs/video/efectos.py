import json, subprocess, numpy as np, os, sys
SFX = sys.argv[1]  # carpeta de la librería (datos externos, solo se leen como audio)
SR = 48000
ev = json.load(open('eventos.json'))
def carga(n):
    raw = subprocess.run(['ffmpeg','-v','error','-i',os.path.join(SFX,n+'.mp3'),'-f','f32le','-ac','2','-ar',str(SR),'-'],capture_output=True,check=True).stdout
    return np.frombuffer(raw,dtype=np.float32).reshape(-1,2)
cues = []
def cue(t, n, v): cues.append((t/1000.0, n, v))
base = 0
for e in ev:
    sid, D, items = e['id'], e['D'], e['items']
    heroes = [i for i in items if i['a'] in ('right','up','scale') and any(k in i['cls'] for k in ('navegador','telefono','boleta','libreta'))]
    for h in heroes: cue(base + h['t'], 'whoosh-short', 0.26 if 'n2' not in h['cls'] and 'n3' not in h['cls'] else 0.2)
    ultimo_pop = -1e9
    for i in sorted(items, key=lambda x: x['t']):
        c, t = i['cls'], base + i['t']
        if i['a'] == 'pop':
            if 'check-grande' in c or 'sello-sunat' in c: cue(t, 'chime', 0.30)
            elif 'bola' in c: cue(t, 'sparkle', 0.20)
            elif t - ultimo_pop >= 140: cue(t + 30, 'pop', 0.16); ultimo_pop = t
        elif i['a'] == 'foco': cue(t, 'ping', 0.09)
        elif i['a'] == 'draw' and 'path' in c and sid == 'conciliacion': cue(t, 'click-soft', 0.22)
        elif i['a'] == 'scale' and 'escribiendo' in c: cue(t, 'typing', 0.12)
        elif i['a'] == 'scale' and 'burbuja' in c: cue(t, 'notification', 0.24)
        elif i['a'] == 'grow' and 'linea' in c: cue(t, 'whoosh-short', 0.18)
        elif i['a'] == 'up' and 'principio' in c: cue(t, 'whoosh-short', 0.14)
        if i['press'] is not None: cue(base + i['press'], 'click-soft', 0.34)
    if sid in ('portada', 'cierre'):
        logo = next(i for i in items if 'mono' in i['cls']); cue(base + logo['t'] + 120, 'impact-bass-1', 0.30)
        tit = next(i for i in items if i['a'] == 'mask'); cue(base + tit['t'] + 250, 'sparkle', 0.16)
    if sid == 'lo-que-viene':
        tit = next(i for i in items if i['a'] == 'mask'); cue(base + tit['t'], 'whoosh', 0.26)
    if sid in ('hoy','propuesta'):
        for i in items:
            if i['a'] == 'up' and 'tarjeta' in i['cls']: cue(base + i['t'], 'whoosh-short', 0.13)
    base += D
total = base / 1000.0
mix = np.zeros((int(total * SR) + SR, 2), dtype=np.float32)
cache = {}
for t, n, v in cues:
    if n not in cache: cache[n] = carga(n)
    s = cache[n]; i0 = int(t * SR); i1 = min(len(mix), i0 + len(s)); mix[i0:i1] += s[:i1 - i0] * v
mix = mix[:int(total * SR)]
pico = float(np.abs(mix).max()); print('cues', len(cues), 'pico', round(pico, 3), 'duracion', total)
if pico > 0.89: mix *= 0.89 / pico
mix.astype(np.float32).tofile('efectos.f32')
json.dump([{'t': round(t, 3), 'sfx': n, 'vol': v} for t, n, v in cues], open('efectos-cues.json', 'w'), indent=0)
