#!/usr/bin/env python3
"""Real mKCP byte transfer: old 26.3.27 server, each candidate client. No APK build or public server."""
import argparse,json,pathlib,socket,subprocess,tempfile,time,threading,http.server
from xray_candidate_schema import migrate
p=argparse.ArgumentParser();p.add_argument('--corpus',type=pathlib.Path,required=True);p.add_argument('--cores',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=b'ghajar-regression-ok';self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
http=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=http.serve_forever,daemon=True).start()
def port(udp=False):
 with socket.socket(type=socket.SOCK_DGRAM if udp else socket.SOCK_STREAM) as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
def wait(port,proc):
 for _ in range(80):
  if proc.poll() is not None:raise RuntimeError('Core exited')
  try:
   with socket.create_connection(('127.0.0.1',port),.1):return
  except OSError:time.sleep(.05)
 raise TimeoutError('Core did not listen')
results=[]
for f in sorted(a.corpus.glob('mkcp-*.json')):
 for version in ('26.3.27','26.7.28','26.9.30'):
  procs=[]
  try:
   original=json.loads(f.read_text())['outbounds'][0];sp=port(True);cp=port()
   server=dict(log=dict(loglevel='warning'),inbounds=[dict(listen='127.0.0.1',port=sp,protocol='vless',settings=dict(clients=[dict(id='00000000-0000-4000-8000-000000000001')],decryption='none'),streamSettings=original['streamSettings'])],outbounds=[dict(protocol='freedom')])
   original['settings']['vnext'][0]['port']=sp
   client=migrate(dict(log=dict(loglevel='warning'),inbounds=[dict(listen='127.0.0.1',port=cp,protocol='socks',settings=dict(udp=False))],outbounds=[original]),version)
   with tempfile.TemporaryDirectory(prefix='ghajar-mkcp-') as td:
    for name,v,config in [('server','26.3.27',server),('client',version,client)]:
     path=pathlib.Path(td)/(name+'.json');path.write_text(json.dumps(config))
     procs.append(subprocess.Popen([str(a.cores/v/'xray'),'run','-config',str(path)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL))
    wait(cp,procs[-1])
    with socket.create_connection(('127.0.0.1',cp),2) as sock:
     sock.settimeout(8);sock.sendall(b'\x05\x01\x00');assert sock.recv(2)==b'\x05\x00'
     sock.sendall(b'\x05\x01\x00\x01\x7f\x00\x00\x01'+http.server_address[1].to_bytes(2,'big'))
     reply=sock.recv(10);assert len(reply)>=2 and reply[1]==0
     sock.sendall(b'GET / HTTP/1.0\r\nHost: localhost\r\n\r\n');data=b''
     while b'ghajar-regression-ok' not in data:
      chunk=sock.recv(8192)
      if not chunk:break
      data+=chunk
     assert b'ghajar-regression-ok' in data
   results.append(dict(case=f.stem,client=version,server='26.3.27',status='PASS'))
  except Exception as e:results.append(dict(case=f.stem,client=version,server='26.3.27',status='FAIL',error=type(e).__name__))
  finally:
   for proc in procs:proc.terminate()
   for proc in procs:
    try:proc.wait(timeout=2)
    except subprocess.TimeoutExpired:proc.kill();proc.wait()
http.shutdown();a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(results,indent=2));print({s:sum(r['status']==s for r in results) for s in ('PASS','FAIL')})
