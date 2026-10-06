// Builds GhajarVPN-iPhone.mobileconfig: a configuration profile that puts
// the Ghajar web app on the iPhone/iPad home screen as a full-screen app.
// Download it in Safari, then Settings → "Profile Downloaded" → Install.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const url = process.env.GHAJAR_URL || 'https://httpuser87890.ir/Faoxima/Ghajarvpn/pwa/'
const out = process.argv[2] || join(here, '..', 'dist', 'GhajarVPN-iPhone.mobileconfig')
const icon = readFileSync(join(here, 'icon-180.png')).toString('base64').replace(/.{1,68}/g, '\t\t\t$&\n')
// Fixed identifiers: installing a newer copy replaces the old one instead of adding a second icon.
const clipUuid = '6C0B3E2A-7F41-4E8B-9C5D-1A2B3C4D5E61'
const profileUuid = '9F2D8C71-3B6E-4A05-8D1C-7E6F5A4B3C29'
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>PayloadContent</key>
	<array>
		<dict>
			<key>FullScreen</key>
			<true/>
			<key>Icon</key>
			<data>
${icon}			</data>
			<key>IgnoreManifestScope</key>
			<true/>
			<key>IsRemovable</key>
			<true/>
			<key>Label</key>
			<string>قاجار</string>
			<key>PayloadDescription</key>
			<string>آیکن برنامهٔ قاجار وی پی ان روی صفحهٔ اصلی</string>
			<key>PayloadDisplayName</key>
			<string>قاجار وی پی ان</string>
			<key>PayloadIdentifier</key>
			<string>com.ghajarvpn.webclip.${clipUuid}</string>
			<key>PayloadType</key>
			<string>com.apple.webClip.managed</string>
			<key>PayloadUUID</key>
			<string>${clipUuid}</string>
			<key>PayloadVersion</key>
			<integer>1</integer>
			<key>Precomposed</key>
			<true/>
			<key>URL</key>
			<string>${esc(url)}</string>
		</dict>
	</array>
	<key>PayloadDescription</key>
	<string>برنامهٔ قاجار وی پی ان را روی صفحهٔ اصلی آیفون نصب می‌کند: فروشگاه، سرویس‌ها و اعلان‌ها.</string>
	<key>PayloadDisplayName</key>
	<string>قاجار وی پی ان</string>
	<key>PayloadIdentifier</key>
	<string>com.ghajarvpn.app.profile</string>
	<key>PayloadOrganization</key>
	<string>Ghajar VPN</string>
	<key>PayloadRemovalDisallowed</key>
	<false/>
	<key>PayloadType</key>
	<string>Configuration</string>
	<key>PayloadUUID</key>
	<string>${profileUuid}</string>
	<key>PayloadVersion</key>
	<integer>1</integer>
</dict>
</plist>
`
mkdirSync(dirname(out), { recursive: true })
writeFileSync(out, xml)
console.log(out)
