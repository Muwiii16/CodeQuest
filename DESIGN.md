---
name: CodeQuest
description: Art direction and UI tokens for CodeQuest, a tower-defense style game that teaches introductory C.
colors:
  # Student UI (values taken from StudentScreen.java / CosmeticLook.java)
  background-deep: "#0A0D1A"
  card: "#141C3D"
  row: "#0F1430"
  button: "#293875"
  accent-teal: "#2EC4B6"
  good: "#246B57"
  gold: "#FFCC4D"
  muted-text: "#9EADE0"
  text: "#FFFFFF"
  feedback-correct: "#8CE6A6"
  feedback-wrong: "#FF8C80"
  # Rarity
  rarity-common: "#C7CCDB"
  rarity-rare: "#59A6FF"
  rarity-epic: "#BD73FF"
  rarity-legendary: "#FFCC40"
  # Professor UI (ProfessorScreen.java): same hues, quieter and denser
  prof-card: "#212633"
  prof-row: "#1A1F2B"
  prof-button: "#333D54"
  prof-good: "#29734D"
  prof-muted-text: "#9EA8C2"
screen:
  virtual-size: "1920x1080"
  scaling: "FitViewport, letterboxed"
---

# CodeQuest Design

## One-sentence intent
A friendly, bright-on-dark cartoon world where answering C questions correctly is the "attack",
so every screen should feel like a game first and a classroom tool second.

## References and what we borrow
| Reference | Borrow | Do not borrow |
|---|---|---|
| Duolingo | Lesson path, streak/reward moments, one mascot, chunky rounded buttons, saturated colour on dark navy | Its owl, exact greens, or layouts |
| Brilliant | Dark panels with gold and green accents, clean question cards, clear progress | Its adult, minimal tone for characters |
| LEGO | Chunky readable shapes, thick outlines, flat primary colour, collectible-item feel | Brand shapes (studs, minifigure proportions) |
| `fantasy` skill | Blue and gold game palette idea, "bold, premium" mood | Its fonts and white surface |
| `sega` skill | Chunky buttons that press in, arcade energy | Pixel typeface, 0px corners (we are not pixel art) |

Do not copy any product's artwork or logo. These are mood references only.

## Style
- **True pixel art** (decision after the team's reference sheet): sprites are drawn at real resolution and shown at integer
  scale (2x-4x) with the LibGDX texture filter set to `Nearest` (`Texture.setFilter(Nearest, Nearest)`). Never smoothed, never rotated freely
  except the floating weapon during its strike.
- **Outline:** `#14121C`, 1 art pixel on sprites; UI panels and buttons use a 4 px notched border (no rounded corners).
- **Shading:** three tones per colour (base, shadow, highlight). No gradients; rarity glow is flat rings and sparkles.
- **Light direction:** top-left, always.
- **Silhouette first:** every tower, hat and weapon must be recognisable as a solid shape at its smallest size.
- **Environment (current):** backgrounds, maps and towers come from the team's environment tileset sheet (school, gate, lab, house,
  trees, bushes, props, grass/stone/sand tiles, six towers). Backgrounds are 960x540 (scale 2x in game), stage maps 1640x902
  (20x11 cells of 82 px, path on whole cells), towers about 98x152. My earlier hand-drawn towers, maps and backgrounds are superseded.
- **Fonts (proposal, not final):** Pixelify Sans for UI, VT323 for code.
- **Hero source (current):** the team's second sheet (checkerboard removed, edges cleaned). Hero sprites are used at full sheet resolution,
  whose pixel blocks are about 3 px, so draw items at 3x their native size next to the hero (hats 186x156, weapons 198x72, towers 114x150).
  On maps the hero is a 1/3-size snapped copy so it matches the tower scale.
- **Native sizes:** hero concept 186x329, run frames about 83-115 x 124-136, hats 62x52, weapons 66x24, towers 38x50, projectile 24x24,
  maps 480x264 (24 px per grid cell), backgrounds 220x124 (shown at 8x to fill 1920x1080 is not intended: draw the final ones at 480x270 or larger).

## Colour rules
- Dark navy is the world; colour is for things the player should look at.
- `accent-teal` = interactive and current focus. `gold` = reward, rarity, certificates, the "next" thing.
- Green = correct and go. Red/coral = wrong and damage. Never use colour alone: pair it with an icon or a word.
- Rarity colours are fixed everywhere (cards, auras, borders, text): common grey-blue, rare blue, epic purple, legendary gold.
- Professor screens use the same hues but darker, less saturated and flatter. No mascot, no glow effects.

## Characters and items
- **Avatar (final design = the team's reference sheet, used as is):** a chibi student with messy black hair, big dark eyes, navy tee,
  dark backpack, dark jeans and white sneakers. The sprites are cut directly from the sheet (background removed, violet effects
  not used), not redrawn: concept, 4 expressions, 5 turnaround views, 5 idle frames, 5 run frames in each of 4 directions.
  Because the sheet is soft pixel art, hats, weapons, towers and UI icons should be redrawn to match it
  (dark outline, chunky shapes) rather than the smooth vector placeholders on the first art-direction boards.
  Keep the sprite crisp: scale by whole numbers with nearest-neighbour filtering. Hats sit on a fixed anchor on top of the hair.
- **No magic effects on attacks.** The only moving thing in a strike is the floating weapon (hover, wind-up, strike, impact).
  The violet psychic effects in the reference sheet are not used.
- **Hats (category "hat"):** each has a distinct top silhouette (cap, wizard, crown, antenna...). Rarer = more ornate, still the same size.
- **Floating weapons (category "weapon"):** one sword-like shape family (drawn about 180 x 68), pointing right, one handle end for the rotation origin.
  Same strike animation for all; rarity changes only look and aura.
- **Trails (category "trail"):** soft round glow dots; colour carries the identity.
- **Titles (category "title"):** text only, gold.
- **Rarity effects:** common none; rare thin glow; epic glow + slow pulse; legendary stronger glow + sparkle particles.
  Effects are separate textures (`glow`, `spark`, `ring`), not baked into item art.

## Gameplay art
- **Towers/enemies/projectiles:** the projectile is a question-mark style orb that reads at 48 px; a wrong answer makes it flash coral, a hit freezes it in blue ice then knocks it back.
- **Stage maps:** top-down, path in a lighter tone than the ground, 20-column grid. The path corners must match `GameMap` exactly; art is drawn around the path, not the other way round.
- **Backgrounds:** 1920 x 1080, calm and low-contrast so UI stays readable; one focal landmark per scene.
- Keep detail near the path and the hero; edges stay simple.

## UI components
- **Button:** filled `button` colour, 3 px lighter border, hover +12% lighter, pressed moves 2-3 px down. Min height 56 px.
- **Card/panel:** `card` fill at 94% opacity, soft border, 16 px radius, 24 px inner padding.
- **Row:** `row` fill with a 8 px left stripe (rarity colour on items, teal on lessons).
- **Text:** white on dark; secondary text `muted-text`. Minimum size 24 px at 1080p; headings 40 px+. Contrast at least 4.5:1.
- **States to design for every control:** default, hover, pressed, disabled, locked ("???" / "Locked"), loading.
- **Spacing:** 8 px grid (8/16/24/32).

## Typography (to be chosen)
Not final. Pick one rounded display face for titles and one clean sans for body. Requirements: free for use and embedding,
supports full ASCII and C punctuation (`{ } ; # * & %`), and a monospaced face for code (`IBM Plex Mono` or `JetBrains Mono`).

## Motion
Short and bouncy: UI transitions 150-250 ms with ease-out; reward reveals 600-900 ms with a small overshoot.
Respect that classroom machines may be slow: nothing depends on motion to be understood.

## Asset rules (from the `game-art` skill)
- Define the style once (this file), then follow it. No mixing art styles.
- Work at source size 2x the in-game size, export at 1x for the game.
- File names match the placeholders in `assets/` so no code changes are needed (paths live in `screens/Art.java`).
- Transparent PNG for everything except full-screen backgrounds.
- Test every item on its real background at its real size before accepting it.

## Accessibility
- Text contrast 4.5:1 or better; large text 3:1.
- Correct/wrong always has a second cue (icon or text), not colour alone.
- Everything clickable is at least 56 px tall at 1080p.

## QA checklist for a new asset
- [ ] Silhouette readable at in-game size
- [ ] Uses only the palette above (rarity colours for rarity only)
- [ ] Outline weight and light direction match
- [ ] Same file name and size as the placeholder it replaces
- [ ] Looks right on dark and on the map background
- [ ] No third-party artwork or logos

## In the game (Nov 2026)
Exported to `assets/` with the placeholder names: `game/maps/map1-3.png` (no towers or hero), `game/towers/tower_*.png`, `game/hero/idle_N.png` and `run_<right|left|up|down>_N.png`,
`game/hats/*.png`, `game/weapons/*.png`, `game/projectile(.png|_frozen.png)`, `game/spark.png`, `ui/backgrounds/*`, `ui/titles/*`, `ui/buttons/*`, `ui/login/card.png`.
The hat and weapon sprites are chosen by item name (see `CosmeticLook`). Backgrounds are 960x540 and drawn at 2x.
