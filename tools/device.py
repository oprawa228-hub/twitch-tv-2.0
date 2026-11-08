"""Native Android evidence. Always targets the new project's named emulator."""
from pathlib import Path
import argparse, re, subprocess, sys, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'.tools/android-sdk/platform-tools/adb.exe'
SERIAL='emulator-5560'
def adb(*args,**kwargs):return subprocess.run([str(ADB),'-s',SERIAL,*args],check=True,**kwargs)
def hierarchy():
    adb('shell','uiautomator','dump','/sdcard/window.xml',stdout=subprocess.DEVNULL)
    return adb('exec-out','cat','/sdcard/window.xml',capture_output=True).stdout
def bounds(node):return tuple(map(int,re.findall(r'\d+',node.get('bounds',''))))
def focus(label):
    for _ in range(24):
        root=ET.fromstring(hierarchy()); nodes=list(root.iter('node'))
        targets=[n for n in nodes if n.get('text')==label or n.get('content-desc')==label]
        if not targets:raise RuntimeError('Visible target not found: '+label)
        target=next((n for n in targets if n.get('focusable')=='true'),targets[0])
        focused=next((n for n in nodes if n.get('focused')=='true'),None)
        if focused is None:adb('shell','input','keyevent','KEYCODE_DPAD_DOWN');continue
        tx1,ty1,tx2,ty2=bounds(target);fx1,fy1,fx2,fy2=bounds(focused)
        tx,ty=(tx1+tx2)/2,(ty1+ty2)/2;fx,fy=(fx1+fx2)/2,(fy1+fy2)/2
        if fx1<=tx<=fx2 and fy1<=ty<=fy2:print('Focused:',label);return
        direction='LEFT' if tx2<=fx1 else 'RIGHT' if tx1>=fx2 else 'UP' if ty<fy else 'DOWN'
        adb('shell','input','keyevent','KEYCODE_DPAD_'+direction)
    raise RuntimeError('D-pad cannot reach '+label)
def observe(name):
    directory=ROOT/'evidence'/'android';directory.mkdir(parents=True,exist_ok=True)
    with open(directory/(name+'.png'),'wb') as f: adb('exec-out','screencap','-p',stdout=f)
    data=hierarchy()
    (directory/(name+'.xml')).write_bytes(data)
    nodes=ET.fromstring(data).iter('node')
    for n in nodes:
        a=n.attrib
        if a.get('text') or a.get('content-desc') or a.get('focused')=='true':
            print({k:a.get(k) for k in ('text','content-desc','focused','bounds')})
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['observe','key','focus']);p.add_argument('value');args=p.parse_args()
    avd=adb('emu','avd','name',capture_output=True,text=True).stdout
    if 'Twitch20Test' not in avd:raise RuntimeError('Wrong emulator')
    if args.action=='observe':observe(args.value)
    elif args.action=='focus':focus(args.value)
    else:adb('shell','input','keyevent',args.value)
