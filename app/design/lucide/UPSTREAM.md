# Lucide icons

Source: https://github.com/lucide-icons/lucide/tree/0.468.0/icons
Version: 0.468.0
License: ISC, with Feather-derived artwork under MIT. Full notices are bundled in
`licenses/Lucide-LICENSE.txt` and `licenses/Feather-LICENSE.txt` (also packaged as app assets).

Only the selected SVGs are vendored. `scripts/generate_icons.py` converts SVG geometry
into cached Compose ImageVectors in `WorkbenchIcons.kt`; `--fetch` downloads this pinned
version. Normal builds need no download or code generation.

The original 24-unit grid, 2-unit stroke and round caps/joins are retained. Compose
supplies theme/disabled tint. Navigation arrows and the trailing navigation chevron
mirror with layout direction; physical objects and media controls do not.
