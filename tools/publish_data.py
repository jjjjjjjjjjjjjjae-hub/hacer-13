#!/usr/bin/env python3
"""Create schema-2 signed inventories and content-addressed DATA blobs. Offline key only."""
import argparse,base64,hashlib,json,pathlib,re
from cryptography.hazmat.primitives.serialization import load_pem_private_key,load_der_public_key,Encoding,PublicFormat
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

def validate(name,data):
    if name=='menu.json':
        c=json.loads(data)
        if set(c)!={'title','features'} or not isinstance(c['title'],str) or not 1<=len(c['title'])<=80 or not 1<=len(c['features'])<=24: raise ValueError('Invalid menu')
        ids=set()
        for f in c['features']:
            if set(f)!={'id','title','description','kind'} or f['kind']!='demo' or not re.fullmatch('[a-z0-9_-]{1,32}',f['id']) or f['id'] in ids: raise ValueError('Only unique demo features allowed')
            ids.add(f['id'])
            if not all(isinstance(f[k],str) and 1<=len(f[k])<=n for k,n in [('title',70),('description',180)]): raise ValueError('Invalid feature text')
    elif name=='theme.json':
        t=json.loads(data)
        if set(t)!={'accent'} or not re.fullmatch('#[A-Fa-f0-9]{6}',t['accent']): raise ValueError('Invalid theme')
    elif re.fullmatch(r'resources/[a-z0-9_-]{1,32}\.png',name):
        import struct
        if len(data)<33 or data[:8]!=b'\x89PNG\r\n\x1a\n' or data[8:16]!=b'\0\0\0\rIHDR' or not all(1<=v<=2048 for v in struct.unpack('>II',data[16:24])): raise ValueError('Invalid PNG')
    else: raise ValueError('Unknown/executable path: '+name)

def publish(key_path,source,out,revision):
    if type(revision)!=int or not 1<=revision<=1000000: raise ValueError('Revision out of range')
    key=load_pem_private_key(pathlib.Path(key_path).read_bytes(),password=None)
    if not isinstance(key,Ed25519PrivateKey): raise ValueError('Ed25519 key required')
    root=pathlib.Path(source);output=pathlib.Path(out);inventory=[];total=0
    files=sorted(p for p in root.rglob('*') if p.is_file())
    if not 1<=len(files)<=32: raise ValueError('Inventory count out of range')
    for p in files:
        if p.is_symlink() or any(x.is_symlink() for x in p.parents if x!=root.parent): raise ValueError('Symlink rejected')
        name=p.relative_to(root).as_posix();data=p.read_bytes();validate(name,data)
        if not 1<=len(data)<=(32768 if name.endswith('.json') else 2*1024*1024): raise ValueError('File too large')
        total+=len(data);h=hashlib.sha256(data).hexdigest();inventory.append({'path':name,'sha256':h,'size':len(data)})
    if not any(x['path']=='menu.json' for x in inventory) or total>8*1024*1024: raise ValueError('Incomplete/oversized bundle')
    manifest={'schema':2,'channel':'lac','revision':revision,'min_core':1,'files':inventory}
    raw=json.dumps(manifest,separators=(',',':'),sort_keys=True).encode()
    envelope={'algorithm':'Ed25519','payload_b64':base64.b64encode(raw).decode(),'signature_b64':base64.b64encode(key.sign(raw)).decode()}
    # All validation precedes writes. The manifest is replaced only after every blob exists.
    (output/'blobs').mkdir(parents=True,exist_ok=True)
    for p,item in zip(files,inventory):
        target=output/'blobs'/item['sha256'];data=p.read_bytes()
        if hashlib.sha256(data).hexdigest()!=item['sha256']: raise ValueError('Source changed during publication')
        target.write_bytes(data)
    tmp=output/'release-v2.tmp';tmp.write_text(json.dumps(envelope,indent=2)+'\n');tmp.replace(output/'release-v2.json')
    print('Signed revision',revision,'files',len(files),'total',total,'bytes')
    return manifest

if __name__=='__main__':
    a=argparse.ArgumentParser(description=__doc__);a.add_argument('key');a.add_argument('source');a.add_argument('output');a.add_argument('--revision',required=True,type=int);o=a.parse_args();publish(o.key,o.source,o.output,o.revision)
