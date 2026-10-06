// The small bridge the web app sees as window.ghajarDesktop.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('ghajarDesktop', {
  version: 1,
  focus: () => ipcRenderer.send('ghajar:focus'),
  openExternal: url => ipcRenderer.send('ghajar:open-external', String(url)),
  openPayment: url => ipcRenderer.send('ghajar:open-payment', String(url)),
  retry: () => ipcRenderer.send('ghajar:retry'),
  // The connection core: a local proxy the Ghajar browser extension uses, so only the browser is connected.
  core: {
    status: () => ipcRenderer.invoke('ghajar:core', 'status'),
    servers: () => ipcRenderer.invoke('ghajar:core', 'servers'),
    setService: service => ipcRenderer.invoke('ghajar:core', 'setService', service),
    connect: index => ipcRenderer.invoke('ghajar:core', 'connect', index),
    disconnect: () => ipcRenderer.invoke('ghajar:core', 'disconnect'),
    ping: () => ipcRenderer.invoke('ghajar:core', 'ping'),
    setDirectIran: on => ipcRenderer.invoke('ghajar:core', 'directIran', !!on),
    onStatus: callback => {
      const listener = (_e, st) => callback(st)
      ipcRenderer.on('ghajar:core-status', listener)
      return () => ipcRenderer.removeListener('ghajar:core-status', listener)
    }
  }
})
