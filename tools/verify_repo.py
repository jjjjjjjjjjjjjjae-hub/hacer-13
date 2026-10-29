#!/usr/bin/env python3
"""Reject oversized Git blobs, game archives, and private signing material before push."""
import pathlib,subprocess,sys,re
bad=[]
for name in subprocess.check_output(['git','ls-files','-z']).decode().split('\0'):
    if not name:continue
    p=pathlib.Path(name)
    if not p.exists():continue
    data=p.read_bytes()
    if len(data)>=90*1024*1024 or p.suffix.lower() in {'.apk','.obb','.xapk','.jks','.keystore','.pem','.key'} or re.search(rb'(?m)^-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\r?$',data):bad.append(name)
if bad:sys.exit('Forbidden files: '+', '.join(bad))
print('Tracked files are below 90 MiB; no game archives or private key files.')
