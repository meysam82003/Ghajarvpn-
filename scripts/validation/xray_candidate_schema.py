"""Qualification-only adapter. Production stays on 26.3.27 until Android/binary gates pass.
Source: v26.7.28 and v26.9.30 infra/conf/transport_finalmask.go MkcpLegacy,
transport/internet/finalmask/finalmask.go (reversed mask application order).
"""
import copy

def migrate(config, version):
    out=copy.deepcopy(config)
    if version=='26.3.27':return out
    if version not in ('26.7.28','26.9.30'):raise ValueError('Unreviewed Xray schema')
    for outbound in out.get('outbounds',[]):
        stream=outbound.get('streamSettings',{})
        masks=stream.get('finalmask',{}).get('udp')
        if not masks:continue
        migrated=[]
        for mask in masks:
            typ=mask['type']; settings=mask.get('settings',{})
            if typ=='mkcp-original':mask=dict(type='mkcp-legacy',settings={})
            elif typ=='mkcp-aes128gcm':mask=dict(type='mkcp-legacy',settings=dict(value=settings['password']))
            elif typ in ('header-dns','header-dtls','header-srtp','header-utp','header-wechat','header-wireguard'):
                mask=dict(type='mkcp-legacy',settings=dict(header=typ.removeprefix('header-'),value=settings.get('domain','')))
            migrated.append(mask)
        stream['finalmask']['udp']=list(reversed(migrated))
    return out
