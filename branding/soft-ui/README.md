# Ghajar soft UI assets

These project assets are versioned in Git, and `scripts/install-ui-assets.sh`
installs them into `app/src/main/res/drawable-nodpi` after patch reconstruction.
The large reviewed base patch is not rewritten to carry binary illustrations.

- `ghajar_wordmark.png`: gold Persian Ghajar / VPN wordmark and crown, true alpha.
- `ghajar_treasury.png`: Qajar treasury minister holding a shopping basket, true alpha.
- `ghajar_welcome_world.png`, `ghajar_welcome_connection.png`,
  `ghajar_welcome_locations.png`: user posters, edited to replace GRoute and its
  antenna with the supplied gold Ghajar wordmark, without a background rectangle.
- `ghajar_welcome_royal.png`: the user's already-Ghajar dark portrait.

Image edits were made with the built-in image generator. Prompt intent:
preserve the supplied composition and Ghajar text; extract the exact wordmark
on transparent alpha; replace only obsolete branding in the three light posters;
derive the treasury minister from the supplied character with a basket of coins
and a shield, navy/gold/emerald, no background or extra text.

The intro uses `ContentScale.Fit` and a 0.985-to-1 fade/scale entrance, never a
crop or an oversized zoom. It is not a generated character video. The first
visit can be swiped and dismissed immediately; returning visits close after
1.8 seconds. Printed server names and counters in the posters are illustrations,
explicitly labelled as such, not real connection status or real inventory.

Navigation icons are new native Android vectors (palace, crowned basket,
gateway/terminal, compass, jewel sliders). Existing navigation destinations and
accessible labels remain unchanged. No image is used as a replacement for a
functional button.
