"""Create this application's persistent signing identity outside the repository."""
from pathlib import Path
import argparse
import os, secrets, shutil, subprocess

root=Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--key-dir', required=True, type=Path, help='External directory for the signing identity')
parser.add_argument('--backup-dir', required=True, type=Path, help='Separate external backup directory')
args = parser.parse_args()
private, backup = args.key_dir.resolve(), args.backup_dir.resolve()
if any(path.is_relative_to(root) or root.is_relative_to(path) for path in (private, backup)):
    parser.error('Choose dedicated directories outside the project.')
if private.is_relative_to(backup) or backup.is_relative_to(private):
    parser.error('Choose separate key and backup directories.')
if (root / 'signing.properties').exists() and not (private / 'release.jks').is_file():
    parser.error('This checkout already has a signing identity. Select its existing key directory.')
private.mkdir(parents=True,exist_ok=True)
key=private/'release.jks'
credentials=private/'signing.properties'
project_credentials = root / 'signing.properties'
if project_credentials.exists() and (
    not credentials.is_file() or credentials.read_bytes() != project_credentials.read_bytes()
):
    parser.error('The selected signing identity differs from this checkout.')
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
    for child in (folder / key.name, folder / credentials.name):
        subprocess.run(['icacls',str(child),'/inheritance:r','/grant:r',os.environ['USERNAME']+':F'],check=True,capture_output=True)
print('Persistent signing key and separate local backup prepared; no secrets printed.')
