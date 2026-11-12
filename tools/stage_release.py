"""Stage an APK and immutable update metadata; never uploads or overwrites a release."""
from pathlib import Path
import argparse
import hashlib,json,re,shutil,subprocess
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk',type=Path)
parser.add_argument('--notes',type=Path)
args=parser.parse_args()
apk=args.apk.resolve()
buildtools=root/'.tools/android-sdk/build-tools/35.0.0'
signed=subprocess.run([str(buildtools/'apksigner.bat'),'verify','--print-certs',str(apk)],capture_output=True,text=True,check=True)
certificate=re.search(r'certificate SHA-256 digest: ([0-9a-f]{64})',signed.stdout).group(1)
badging=subprocess.run([str(buildtools/'aapt.exe'),'dump','badging',str(apk)],capture_output=True,text=True,encoding='utf-8',check=True).stdout
package,code,name=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging).groups()
minimum=int(re.search(r"sdkVersion:'(\d+)'",badging).group(1))
assert package=='com.oprawa.twitch20'
assert certificate=='f81f51fd47d318aaf79f1fe3c81159fe6eca405f881f4f0b19d097d1c4b006f7'
tag='v'+name;dest=root/'dist'/tag;dest.mkdir(parents=True,exist_ok=True)
filename='Twitch20-'+name+'.apk'
target=dest/filename
if target.exists() and target.read_bytes()!=apk.read_bytes():raise RuntimeError('Staged artifact differs: use a new candidate directory/version')
shutil.copy2(apk,target)
digest=hashlib.sha256(target.read_bytes()).hexdigest()
manifest=dict(packageName=package,versionCode=int(code),versionName=name,minSdk=minimum,
    apkUrl=f'https://github.com/oprawa228-hub/twitch-tv-2.0/releases/download/{tag}/{filename}',
    sha256=digest,certificateSha256=certificate)
if args.notes:
    notes=args.notes.read_text(encoding='utf-8-sig').strip()
    if not 0 < len(notes) <= 8192:
        raise ValueError('Release notes must contain 1 to 8192 characters')
    manifest['notes']=notes
manifest_text=json.dumps(manifest,indent=2,ensure_ascii=False)+'\n'
manifest_path=dest/'update.json'
if manifest_path.exists() and manifest_path.read_text(encoding='utf-8')!=manifest_text:
    raise RuntimeError('Staged metadata differs: use a new candidate directory/version')
manifest_path.write_text(manifest_text,encoding='utf-8')
(dest/'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n',encoding='utf-8')
print(json.dumps(dict(directory=str(dest),**{key:value for key,value in manifest.items() if key!='notes'}),indent=2))
