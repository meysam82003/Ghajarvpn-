// The small bridge the web app sees as window.ghajarDesktop.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('ghajarDesktop', {
  version: 1,
  focus: () => ipcRenderer.send('ghajar:focus'),
  openExternal: url => ipcRenderer.send('ghajar:open-external', String(url)),
  openPayment: url => ipcRenderer.send('ghajar:open-payment', String(url)),
  retry: () => ipcRenderer.send('ghajar:retry')
})
