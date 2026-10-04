package com.ghajarvpn.browser
interface BrowserDocumentStore { fun load(key:String):String?; fun save(key:String,value:String); fun remove(key:String) }
