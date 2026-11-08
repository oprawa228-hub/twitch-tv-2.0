"""Release operator using Git Credential Manager; never prints credentials."""
import argparse, json, os, subprocess, sys, urllib.request, urllib.error

def request(method, path, body=None):
    env = dict(os.environ, GCM_INTERACTIVE='Never', GIT_TERMINAL_PROMPT='0')
    proc = subprocess.run(['git','credential','fill'], input='protocol=https\nhost=github.com\n\n', text=True, capture_output=True, env=env)
    values = dict(line.split('=',1) for line in proc.stdout.splitlines() if '=' in line)
    token = values.get('password')
    if not token:
        raise RuntimeError('No GitHub credential available from Git Credential Manager')
    url = path if path.startswith('https://') else 'https://api.github.com'+path
    if not (url.startswith('https://api.github.com/') or url.startswith('https://uploads.github.com/')):
        raise ValueError('Unsupported GitHub API host')
    data = json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(url,data=data,method=method,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','Content-Type':'application/json','User-Agent':'Twitch20-release'})
    with urllib.request.urlopen(req, timeout=45) as response: return json.load(response)

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('method'); parser.add_argument('path'); parser.add_argument('--body-file'); args=parser.parse_args()
    body=json.load(open(args.body_file,encoding='utf-8-sig')) if args.body_file else None
    try:
        result=request(args.method,args.path,body)
        # Allowlisted public metadata only.
        if isinstance(result,dict): print(json.dumps({k:result[k] for k in ('id','login','name','full_name','html_url','upload_url','tag_name','message') if k in result},ensure_ascii=False))
        else: print(json.dumps(result,ensure_ascii=False))
    except urllib.error.HTTPError as e: print('GitHub HTTP',e.code); sys.exit(1)
    except Exception as e: print(str(e)); sys.exit(1)
