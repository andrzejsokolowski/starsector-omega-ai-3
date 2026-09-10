# Omega AI sprite

The project uses a generated Omega emblem in the LunaLib settings list, combat status, and fleet trial.
The asset is `graphics/omega_ai_icon.png`.
`data/config/LunaSettingsConfig.json` registers that image under the `omega_ai3` mod ID, following Sol Renewed's configuration.
The mission contains an identical copy at `data/missions/omega_ai3_trial/icon.png`.
The repository also contains `omega-ai-icon.png` for the README and forum post draft.
That documentation image stays outside the runtime ZIP.
The PNG is a square RGBA image, which includes an alpha channel for transparency.

The built-in image generation tool created the sprite for this project.
The project preserves its generated pixels and alpha channel.
No external reference image or existing game sprite was used.

Final generation prompt:

> Use case: stylized-concept. Asset type: final square game-mod icon sprite for a Starsector mod named Omega AI. Create one compact, sharply readable emblem: a heavy Greek uppercase Omega symbol integrated into a mechanical spacecraft command core, with three small coordinated tactical chevrons arranged beneath the arch to suggest a fleet acting together. Industrial science-fiction game inventory art, restrained dimensional painted metal, precise beveled edges, a small luminous energy core, strong silhouette that reads at 64 pixels. Centered front view, generous clear margin, balanced symmetric composition. Dark gunmetal with cool luminous highlights appropriate to a space combat interface. Square canvas, genuinely transparent background, clean alpha, no scene or backdrop, no lettering except the Omega glyph, no additional labels, no watermark. It should be a polished practical UI sprite, not a poster, photograph, or app mockup.
