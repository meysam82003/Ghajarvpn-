#!/usr/bin/env python3
"""Pinned Xray data-plane check using Ghajar-imported .conf. No Android build or public VPN.
Adds a SOCKS listener to buildForTest output; leaves generated outbounds and DNS untouched.
"""
import argparse,base64,hashlib,json,pathlib,socket,struct,subprocess,tempfile,threading,time,http.server,os
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives import serialization
p=argparse.ArgumentParser();p.add_argument('--core',type=pathlib.Path,required=True);p.add_argument('--dependencies',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
a.core=a.core.resolve();deps=a.dependencies.resolve();repo=pathlib.Path(__file__).resolve().parents[2]
assert 'Xray 26.3.27 ' in subprocess.check_output([str(a.core),'version'],text=True)
cp=':'.join(map(str,[deps/'phase3-tests.jar',deps/'kotlin-stdlib-2.1.0.jar',deps/'json-20240303.jar',deps/'android-all-15.jar']))
subprocess.run(['java','-cp',str(deps/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(deps/'wireguard-corpus.jar'),str(repo/'scripts/validation/WireGuardCorpus.kt')],check=True,capture_output=True)
def key():
 k=X25519PrivateKey.generate();return (base64.b64encode(k.private_bytes(serialization.Encoding.Raw,serialization.PrivateFormat.Raw,serialization.NoEncryption())).decode(),base64.b64encode(k.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)).decode())
def port(udp=False):
 with socket.socket(type=socket.SOCK_DGRAM if udp else socket.SOCK_STREAM) as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def recv(s,n):
 b=b''
 while len(b)<n:
  part=s.recv(n-len(b))
  if not part:raise EOFError()
  b+=part
 return b
hits=0;dns_hits=0
payload=b'ghajar-wireguard-real-transfer'*8192
class HTTP(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  global hits
  hits+=1;self.send_response(200);self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
 def log_message(self,*args):pass
class HTTP6(http.server.ThreadingHTTPServer):address_family=socket.AF_INET6
servers=[http.server.ThreadingHTTPServer(('127.0.0.1',0),HTTP),HTTP6(('::1',0),HTTP)]
for h in servers:threading.Thread(target=h.serve_forever,daemon=True).start()
echo=socket.socket(type=socket.SOCK_DGRAM);echo.bind(('127.0.0.1',0));echo.settimeout(.1)
dns=socket.socket(type=socket.SOCK_DGRAM);dns.bind(('127.0.0.1',0));dns.settimeout(.1)
closed=threading.Event()
def udp_loop(sock,is_dns):
 global dns_hits
 while not closed.is_set():
  try:
   b,addr=sock.recvfrom(65535)
   if is_dns:
    dns_hits+=1;i=12
    while b[i]:i+=1+b[i]
    i+=1;question=b[12:i+4];typ=struct.unpack('!H',b[i:i+2])[0]
    answer=(b'\xc0\x0c'+struct.pack('!HHIH',1,1,60,4)+socket.inet_aton('198.18.88.11')) if typ==1 else b''
    b=b[:2]+struct.pack('!HHHHH',0x8180,1,int(bool(answer)),0,0)+question+answer
   sock.sendto(b,addr)
  except socket.timeout:pass
threading.Thread(target=udp_loop,args=(dns,True),daemon=True).start();threading.Thread(target=udp_loop,args=(echo,False),daemon=True).start()
def addr(host,pt):
 try:
  ip=socket.inet_pton(socket.AF_INET,host);return b'\x01'+ip+struct.pack('!H',pt)
 except OSError:pass
 try:
  ip=socket.inet_pton(socket.AF_INET6,host);return b'\x04'+ip+struct.pack('!H',pt)
 except OSError:return b'\x03'+bytes([len(host)])+host.encode()+struct.pack('!H',pt)
def socks(cp,host,pt,udp=False):
 s=socket.create_connection(('127.0.0.1',cp),2);s.settimeout(3)
 try:
  s.sendall(b'\x05\x01\x00');assert recv(s,2)==b'\x05\x00'
  s.sendall(b'\x05'+bytes([3 if udp else 1])+b'\x00'+addr(host,pt));h=recv(s,4);assert h[:2]==b'\x05\x00'
  host2=socket.inet_ntop(socket.AF_INET if h[3]==1 else socket.AF_INET6,recv(s,4 if h[3]==1 else 16));p2=struct.unpack('!H',recv(s,2))[0]
  return s,(host2,p2)
 except BaseException:s.close();raise
def http_get(cp,host,pt):
 s,_=socks(cp,host,pt)
 with s:
  s.sendall(b'GET / HTTP/1.0\r\nHost: fixture.invalid\r\n\r\n');b=b''
  while True:
   more=s.recv(65536)
   if not more:break
   b+=more
  assert b.split(b'\r\n\r\n',1)[1]==payload
procs=[];results=[]
def run(name,test):
 try:test();results.append({'case':name,'status':'PASS'})
 except Exception as e:results.append({'case':name,'status':'FAIL','error':type(e).__name__})
def stopped(p):
 p.terminate()
 try:p.wait(timeout=3)
 except subprocess.TimeoutExpired:p.kill();p.wait()
try:
 with tempfile.TemporaryDirectory(prefix='ghajar-wireguard-') as temp:
  root=pathlib.Path(temp);ck,cpub=key();psk=base64.b64encode(bytes(range(32))).decode();wg=[]
  def start(name,c):
   if os.environ.get('GHAJAR_WG_DEBUG'): c['log']={'loglevel':'debug','error':str(pathlib.Path(os.environ['GHAJAR_WG_DEBUG'])/(name+'.log'))}
   f=root/(name+'.json');f.write_text(json.dumps(c));f.chmod(0o600)
   v=subprocess.run([str(a.core),'run','-test','-config',str(f)],capture_output=True,timeout=15)
   if v.returncode:raise RuntimeError('Core validation rejected synthetic case')
   pr=subprocess.Popen([str(a.core),'run','-config',str(f)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);procs.append(pr);return pr
  for i in range(2):
   sk,spub=key();wp=port(True);wg.append((wp,spub))
   start('server'+str(i),{'log':{'loglevel':'none'},'inbounds':[{'listen':'127.0.0.1','port':wp,'protocol':'wireguard','settings':{'secretKey':sk,'address':['10.88.0.1/32','fd88::1/128'],'noKernelTun':True,'mtu':1280,'peers':[{'publicKey':cpub,'preSharedKey':psk,'allowedIPs':['10.88.0.2/32','fd88::2/128']} ]}}], 'outbounds':[{'tag':'destination','protocol':'freedom','settings':{'redirect':('127.0.0.1:' if i==0 else '[::1]:')+str(servers[i].server_address[1])}},{'tag':'echo','protocol':'freedom','settings':{'redirect':'127.0.0.1:'+str(echo.getsockname()[1])}},{'tag':'dns','protocol':'freedom','settings':{'redirect':'127.0.0.1:'+str(dns.getsockname()[1])}}],'routing':{'rules':[{'type':'field','ip':['10.88.0.53'],'port':'53','outboundTag':'dns'},{'type':'field','network':'udp','port':'5354','outboundTag':'echo'}]}})
  conf='[Interface]\nPrivateKey = '+ck+'\nAddress = 10.88.0.2/32, fd88::2/128\nDNS = 10.88.0.53\nMTU = 1280\n'
  for i,(wp,pub) in enumerate(wg):conf+='[Peer]\nPublicKey = '+pub+'\nPresharedKey = '+psk+'\nEndpoint = 127.0.0.1:'+str(wp)+'\nAllowedIPs = '+('198.18.88.11/32, 10.88.0.53/32' if i==0 else 'fd88::11/128')+'\nPersistentKeepalive = 5\n'
  f=root/'input.conf';f.write_text(conf);f.chmod(0o600);generated=root/'generated.json'
  subprocess.run(['java','-cp',str(deps/'wireguard-corpus.jar')+':'+cp,'net.gozar.app.validation.WireGuardCorpusKt',str(f),str(generated)],check=True,capture_output=True)
  client=json.loads(generated.read_text());assert len(client['outbounds'][0]['settings']['peers'])==2
  cpport=port();client['inbounds']=[{'listen':'127.0.0.1','port':cpport,'protocol':'socks','settings':{'udp':True}}]
  pr=start('client',client)
  deadline=time.monotonic()+5
  while True:
   assert pr.poll() is None
   try:
    with socket.create_connection(('127.0.0.1',cpport),.1):break
   except OSError:
    if time.monotonic()>deadline:raise TimeoutError('No readiness listener')
    time.sleep(.025)
  run('ipv4-tcp-through-peer-1',lambda:http_get(cpport,'198.18.88.11',8080))
  run('ipv6-tcp-through-peer-2',lambda:http_get(cpport,'fd88::11',8080))
  def domain():
   http_get(cpport,'only-via-wireguard.invalid',8080);assert dns_hits>0
  run('dns-via-wireguard-and-tcp',domain)
  def udp():
   control,relay=socks(cpport,'0.0.0.0',0,True)
   with control,socket.socket(type=socket.SOCK_DGRAM) as s:
    s.settimeout(3);body=b'wireguard-udp'*64;s.sendto(b'\0\0\0'+addr('198.18.88.11',5354)+body,relay)
    got,_=s.recvfrom(65535);assert got[10:]==body
  run('udp-data-through-peer',udp)
  before=hits
  for pr in procs[:2]:stopped(pr)
  def no_fallback():
   failed=False
   try:http_get(cpport,'198.18.88.11',8080)
   except (OSError,EOFError,AssertionError,IndexError):failed=True
   assert failed and hits==before
  run('peer-death-no-direct-fallback',no_fallback)
finally:
 for pr in procs:
  if pr.poll() is None:stopped(pr)
 closed.set()
 for h in servers:h.shutdown();h.server_close()
 echo.close();dns.close()
report={'coreVersion':'26.3.27','coreSha256':hashlib.sha256(a.core.read_bytes()).hexdigest(),'source':'official XTLS/Xray-core release; Android AAR unchanged','generator':'ConfigParser -> ProxyConfig JSON roundtrip -> ConfigBuilder.buildForTest','testOnlyChange':'SOCKS inbound for local probe; outbounds/DNS unchanged','results':results,'deviceVerified':False}
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(results));assert results and all(r['status']=='PASS' for r in results)
