"""Stage an APK and immutable update metadata; never uploads or overwrites a release."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys
root=Path(__file__).resolve().parents[1]
apk=Path(sys.argv[1]).resolve()
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
(dest/'update.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
(dest/'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n',encoding='utf-8')
print(json.dumps(dict(directory=str(dest),**manifest),indent=2))
