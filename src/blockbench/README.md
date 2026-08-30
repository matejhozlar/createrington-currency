# Blockbench sources

Raw designer files; the shipped assets under `src/main/resources/assets/createringtoncurrency/` are derived from them.

## depositor_terminal

`depositor_terminal.json` / `depositor_terminal.png` are the designer's untouched Blockbench export
(`depositor-terminal-screenless`, 2026-08-30). The shipped `models/block/depositor_terminal.json` is that
export with these edits, which have to be re-applied whenever a new export lands:

- `parent: minecraft:block/block`, `render_type: minecraft:cutout`, textures pointed at
  `createringtoncurrency:block/depositor_terminal`.
- `cullface` only on faces that lie on the block boundary (base and top slab sides, body east/south/west).
  The export puts `cullface: south` on the tilted panel's front face, which makes the whole panel disappear
  as soon as a solid block sits behind the terminal.
- Faces hidden inside other elements are dropped: body north/up/down, panel south/up/down, the bar's south
  face (maps to transparent texels) and the LED's south face (sits on the panel).
- The LED element (the last one) gets `tintindex: 0` on every face, `shade: false` and
  `neoforge_data: {block_light: 15, sky_light: 15, ambient_occlusion: false}`. Its colour comes from the
  block/item colour handlers in `ClientOnlyHooks`, so the texel patch it samples must stay (near) white.
- Texture: the LED's 2x2 texel patch is softened from the export's `ffffff`/`d0d0d0` diagonal to
  `ffffff`/`e8e8e8`; the tint multiplies the texels, so the export's contrast shows as a checkerboard.
