import json, subprocess
esc = json.load(open('escenas2.json')); plan = {p['id']: p for p in json.load(open('plan.json'))}
open('lista2.txt','w').write(''.join(f"file 'clips2/{i+1:02d}.mp4'\n" for i in range(len(esc))))
subprocess.run(['ffmpeg','-v','error','-y','-f','concat','-safe','0','-i','lista2.txt','-f','lavfi','-i','anullsrc=channel_layout=stereo:sample_rate=48000',
  '-map','0:v','-map','1:a','-shortest','-c:v','libx264','-preset','slow','-crf','23','-tune','animation','-pix_fmt','yuv420p','-movflags','+faststart',
  '-c:a','aac','-b:a','64k','Cuentas-Claras-presentacion-animada.mp4'], check=True)
def ts(x): h=int(x//3600); m=int(x%3600//60); s=x%60; return f"{h:02d}:{m:02d}:{int(s):02d},{int(round((s-int(s))*1000)):03d}"
def mmss(x): return f"{int(x//60)}:{int(x%60):02d}"
nombres={'portada':'Portada','hoy':'Cómo se trabaja hoy','propuesta':'La propuesta','principios':'Tres reglas','caja-buscar':'Caja: buscar familia','caja-familia':'Caja: marcar cuotas','caja-confirmacion':'Caja: vuelto y boleta','caja-boleta':'Libro de pagos','caja-cierre':'Cierre a ciegas','aprobaciones':'Aprobaciones','promotora':'Inicio de la promotora','bitacora':'Bitácora','datos':'Alumnos y pensiones','lo-que-viene':'Lo que viene','pagos-digitales':'Pagos digitales','whatsapp':'WhatsApp','portal':'Portal de padres','conciliacion':'Conciliación automática','sunat':'Boleta SUNAT','panel':'Panel en el celular','academico':'Académico','ruta':'Hoja de ruta','cierre':'Cierre'}
t=0.0; srt=[]; filas=[]
for i,e in enumerate(esc):
    D=e['dur']/1000; txt=plan[e['id']]['texto']
    srt += [str(i+1), f"{ts(t+0.4)} --> {ts(t+D-0.6)}", txt, ""]
    filas.append(f"| {mmss(t)} | {nombres[e['id']]} | {txt} |"); t+=D
md=["# Guion de la narración · Cuentas Claras","",f"Duración del video: {mmss(t)}. Lee cada texto cuando aparezca su escena, con calma y en tono cercano. Si te sobra tiempo en una escena, haz una pausa; no hace falta llenarla.","","| Tiempo | Escena | Texto para leer |","|---|---|---|"]+filas
open('Cuentas-Claras-presentacion.srt','w',encoding='utf-8').write("\n".join(srt))
open('Guion-narracion.md','w',encoding='utf-8').write("\n".join(md)+"\n")
print('total', round(t,1))
