// The small bridge the web app sees as window.ghajarDesktop.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('ghajarDesktop', {
  version: 1,
  focus: () => ipcRenderer.send('ghajar:focus'),
  openExternal: url => ipcRenderer.send('ghajar:open-external', String(url)),
  openPayment: url => ipcRenderer.send('ghajar:open-payment', String(url)),
  retry: () => ipcRenderer.send('ghajar:retry'),
  // The VPN engine: the whole device (TUN), chosen apps only, the system proxy, or a local proxy.
  core: {
    version: 2,
    status: () => ipcRenderer.invoke('ghajar:core', 'status'),
    capabilities: () => ipcRenderer.invoke('ghajar:core', 'capabilities'),
    servers: () => ipcRenderer.invoke('ghajar:core', 'servers'),
    groups: () => ipcRenderer.invoke('ghajar:core', 'groups'),
    settings: () => ipcRenderer.invoke('ghajar:core', 'settings'),
    setSettings: patch => ipcRenderer.invoke('ghajar:core', 'setSettings', patch),
    setService: service => ipcRenderer.invoke('ghajar:core', 'setService', service),
    add: text => ipcRenderer.invoke('ghajar:core', 'add', String(text)),
    addSubscription: (url, name) => ipcRenderer.invoke('ghajar:core', 'addSubscription', { url: String(url), name: String(name || '') }),
    refreshSubscriptions: () => ipcRenderer.invoke('ghajar:core', 'refreshSubscriptions'),
    renameSubscription: (id, name) => ipcRenderer.invoke('ghajar:core', 'renameSubscription', { id: String(id), name: String(name) }),
    removeSubscription: id => ipcRenderer.invoke('ghajar:core', 'removeSubscription', String(id)),
    removeConfig: id => ipcRenderer.invoke('ghajar:core', 'removeConfig', String(id)),
    favorite: id => ipcRenderer.invoke('ghajar:core', 'favorite', String(id)),
    select: id => ipcRenderer.invoke('ghajar:core', 'select', String(id)),
    shareLink: id => ipcRenderer.invoke('ghajar:core', 'shareLink', String(id)),
    connect: target => ipcRenderer.invoke('ghajar:core', 'connect', target),
    disconnect: () => ipcRenderer.invoke('ghajar:core', 'disconnect'),
    ping: () => ipcRenderer.invoke('ghajar:core', 'ping'),
    test: ids => ipcRenderer.invoke('ghajar:core', 'test', ids),
    fastest: () => ipcRenderer.invoke('ghajar:core', 'fastest'),
    refreshFree: () => ipcRenderer.invoke('ghajar:core', 'refreshFree'),
    addWarp: () => ipcRenderer.invoke('ghajar:core', 'addWarp'),
    addPsiphon: country => ipcRenderer.invoke('ghajar:core', 'addPsiphon', country || ''),
    runningApps: () => ipcRenderer.invoke('ghajar:core', 'runningApps'),
    setDirectIran: on => ipcRenderer.invoke('ghajar:core', 'directIran', !!on),
    onStatus: callback => {
      const listener = (_e, st) => callback(st)
      ipcRenderer.on('ghajar:core-status', listener)
      return () => ipcRenderer.removeListener('ghajar:core-status', listener)
    }
  }
})
