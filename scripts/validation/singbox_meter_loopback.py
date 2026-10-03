#!/usr/bin/env python3
"""Real pinned-core TCP/UDP data-path and authenticated-probe meter regression."""
import json, socket, struct, subprocess, tempfile, threading, time, urllib.request, urllib.error, sys
from pathlib import Path

def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def recv(s,n):
 b=b''
 while len(b)<n:
  x=s.recv(n-len(b))
  if not x:raise RuntimeError('early EOF')
  b+=x
 return b

def main(binary):
 data,probe,api=port(),port(),port()
 server=socket.socket();server.bind(('127.0.0.1',0));server.listen();target=server.getsockname()[1]
 def echo():
  while True:
   try:c,_=server.accept()
   except OSError:return
   def session(c):
    with c:
     while True:
      b=c.recv(65536)
      if not b:return
      c.sendall(b)
   threading.Thread(target=session,args=(c,),daemon=True).start()
 threading.Thread(target=echo,daemon=True).start()
 udp=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);udp.bind(('127.0.0.1',0));udp_port=udp.getsockname()[1]
 def udp_echo():
  while True:
   try:b,a=udp.recvfrom(65536);udp.sendto(b,a)
   except OSError:return
 threading.Thread(target=udp_echo,daemon=True).start()
 def usage(auth=True,freeze=False):
  req=urllib.request.Request(f'http://127.0.0.1:{api}/ghajar/{"freeze" if freeze else "usage"}',method='POST' if freeze else 'GET',headers={'Authorization':'Bearer test-secret-012345678901234567890'} if auth else {})
  return json.load(urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=3))
 def socks(p,auth=False,command=1,target_port=target):
  s=socket.create_connection(('127.0.0.1',p),3);s.settimeout(3)
  s.sendall(bytes([5,1,2 if auth else 0]));assert recv(s,2)==bytes([5,2 if auth else 0])
  if auth:s.sendall(b'\x01\x05vault\x06secret');assert recv(s,2)==b'\x01\x00'
  s.sendall(bytes([5,command,0,1])+socket.inet_aton('127.0.0.1')+struct.pack('!H',target_port))
  h=recv(s,4);assert h[:3]==b'\x05\x00\x00'
  addr=recv(s,4 if h[3]==1 else 16);p=struct.unpack('!H',recv(s,2))[0]
  return s,(socket.inet_ntop(socket.AF_INET if h[3]==1 else socket.AF_INET6,addr),p)
 cfg={'log':{'level':'error'},'inbounds':[{'type':'socks','tag':'data','listen':'127.0.0.1','listen_port':data},{'type':'socks','tag':'ghajar-vault-probe','listen':'127.0.0.1','listen_port':probe,'users':[{'username':'vault','password':'secret'}]}], 'outbounds':[{'type':'direct','tag':'test-loopback-only'}], 'experimental':{'clash_api':{'external_controller':f'127.0.0.1:{api}','secret':'test-secret-012345678901234567890'}}}
 with tempfile.TemporaryDirectory() as d:
  f=Path(d)/'config.json';f.write_text(json.dumps(cfg));f.chmod(0o600)
  with (Path(d)/'log').open('w') as log:
   proc=subprocess.Popen([binary,'run','-c',str(f)],stdout=log,stderr=log)
   try:
    for _ in range(100):
     try:usage();break
     except Exception:
      if proc.poll() is not None:raise RuntimeError('core failed: '+(Path(d)/'log').read_text())
      time.sleep(.05)
    try:usage(False);raise AssertionError('unauthenticated API accepted')
    except urllib.error.HTTPError as e:assert e.code==401
    print('PASS authenticated API')
    payload=b'x'*4096
    s,_=socks(data)
    with s:s.sendall(payload);assert recv(s,len(payload))==payload
    time.sleep(.05);first=usage();assert (first['upload'],first['download'])==(4096,4096),first
    print('PASS TCP exact payload including closed connection')
    s,_=socks(probe,True)
    with s:s.sendall(payload);assert recv(s,len(payload))==payload
    time.sleep(.05);assert usage()==first
    print('PASS authenticated internal probe excluded')
    s,relay=socks(data,command=3,target_port=0)
    with s,socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
     client.settimeout(3);client.sendto(b'\x00\x00\x00\x01'+socket.inet_aton('127.0.0.1')+struct.pack('!H',udp_port)+b'u'*333,relay)
     packet,_=client.recvfrom(65536);assert packet.endswith(b'u'*333)
    time.sleep(.05);final=usage();assert final['upload']==4429 and final['download']==4429,final
    print('PASS UDP exact payload')
    frozen=usage(freeze=True);assert frozen==final
    try:
     s,_=socks(data)
     with s:
      s.sendall(b'blocked');assert s.recv(7)!=b'blocked'
    except (OSError,RuntimeError):pass
    assert usage()==frozen
    print('PASS final snapshot freezes data path and totals')
   finally:proc.terminate();proc.wait(timeout=5);server.close();udp.close()
if __name__=='__main__':main(sys.argv[1])
