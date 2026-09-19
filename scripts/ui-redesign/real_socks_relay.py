import os, socket, socketserver, struct, urllib.parse, selectors, base64
# Test-only TCP relay. It forwards bytes from real upstream sockets.
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
def receive(s,n):
 b=b''
 while len(b)<n:
  r=s.recv(n-len(b))
  if not r: raise EOFError()
  b+=r
 return b
class Handler(socketserver.BaseRequestHandler):
 def handle(self):
  c=self.request;c.settimeout(30)
  try:
   ver,n=receive(c,2);receive(c,n);c.sendall(b'\x05\x00')
   ver,command,_,kind=receive(c,4)
   if command!=1: return
   if kind==1: host=socket.inet_ntoa(receive(c,4))
   elif kind==3: host=receive(c,receive(c,1)[0]).decode()
   elif kind==4: host=socket.inet_ntop(socket.AF_INET6,receive(c,16))
   else: return
   port=struct.unpack('!H',receive(c,2))[0]
   endpoint=(proxy.hostname,proxy.port or 80) if proxy.hostname else (host,port)
   with socket.create_connection(endpoint,timeout=30) as upstream:
    if proxy.hostname:
     headers=f'CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n'
     if proxy.username:
      auth=base64.b64encode((urllib.parse.unquote(proxy.username)+':'+urllib.parse.unquote(proxy.password or '')).encode()).decode()
      headers+=f'Proxy-Authorization: Basic {auth}\r\n'
     upstream.sendall((headers+'\r\n').encode())
     response=b''
     while not response.endswith(b'\r\n\r\n'): response+=receive(upstream,1)
     if b' 200 ' not in response.split(b'\r\n')[0]: raise IOError('Upstream refused CONNECT')
    c.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x00')
    transferred=0
    with selectors.DefaultSelector() as sel:
     sel.register(c,selectors.EVENT_READ,upstream);sel.register(upstream,selectors.EVENT_READ,c)
     while True:
      ready=sel.select(30)
      if not ready: break
      for key,_ in ready:
       chunk=key.fileobj.recv(65536)
       if not chunk:
        print(f'real relay completed bytes={transferred}',flush=True);return
       key.data.sendall(chunk);transferred+=len(chunk)
  except Exception as e:
   print(type(e).__name__,flush=True)
class Server(socketserver.ThreadingTCPServer):
 allow_reuse_address=True
 daemon_threads=True
with Server(('0.0.0.0',18080),Handler) as server: server.serve_forever()
