"""Create this application's persistent signing identity outside the repository."""
from pathlib import Path
import os, secrets, shutil, subprocess

root=Path(__file__).resolve().parents[1]
private=Path.home()/'.android'/'signing'/'twitch20'
backup=Path('D:/Private-Backups/Twitch20-signing')
private.mkdir(parents=True,exist_ok=True)
key=private/'release.jks'
credentials=private/'signing.properties'
if not key.exists():
    password=secrets.token_urlsafe(36)
    env=dict(os.environ,TWITCH20_KEY_PASSWORD=password)
    java=Path(os.environ['JAVA_HOME'])/'bin'/'keytool.exe'
    subprocess.run([str(java),'-genkeypair','-keystore',str(key),'-storetype','JKS','-alias','twitch20',
        '-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Oprawa Twitch 2.0',
        '-storepass:env','TWITCH20_KEY_PASSWORD','-keypass:env','TWITCH20_KEY_PASSWORD'],env=env,check=True,capture_output=True)
    credentials.write_text(f'storeFile={key.as_posix()}\nstorePassword={password}\nkeyAlias=twitch20\nkeyPassword={password}\n',encoding='utf-8')
if not credentials.exists(): raise RuntimeError('Existing signing key has no configuration; do not replace it.')
backup.mkdir(parents=True,exist_ok=True)
for src in (key,credentials):
    dst=backup/src.name
    if dst.exists() and dst.read_bytes()!=src.read_bytes(): raise RuntimeError('Backup differs; refusing overwrite')
    if not dst.exists(): shutil.copy2(src,dst)
shutil.copy2(credentials,root/'signing.properties')
for folder in (private,backup):
    subprocess.run(['icacls',str(folder),'/inheritance:r','/grant:r',os.environ['USERNAME']+':(OI)(CI)F'],check=True,capture_output=True)
    for child in folder.iterdir():
        subprocess.run(['icacls',str(child),'/inheritance:r','/grant:r',os.environ['USERNAME']+':F'],check=True,capture_output=True)
print('Persistent signing key and separate local backup prepared; no secrets printed.')
