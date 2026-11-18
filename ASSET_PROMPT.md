# CodeQuest asset prompt (for an image AI)

Attach `reference sheet` (the hero character sheet) to the first message. Paste the MASTER PROMPT first, then one ASSET REQUEST at a time.

## MASTER PROMPT

You are a professional 2D game artist. Create game-ready PIXEL ART assets for "CodeQuest", a cute tower-defense game that teaches introductory C programming to students. The attached image is the approved hero character sheet: the hero must match it exactly (chibi proportions, messy black hair, big dark eyes, navy t-shirt, dark backpack, dark jeans, white sneakers).

STYLE RULES (apply to every asset):
- True pixel art on a strict grid. Every art pixel is an exact square block. No anti-aliasing, no blur, no smooth gradients, no noisy dithering, no 3D render look.
- 1 pixel dark outline in #14121C around every sprite.
- Three tones per colour (base, shadow, highlight). Light always comes from the top left; shadows fall to the bottom right.
- Limited palette only: #14121C outline, #0A0D1A deep navy, #141C3D, #293875, #2EC4B6 teal, #FFCC4D gold, #FF8C80 coral, #8CE6A6 green, #9EADE0 light blue-grey; rarity colours #C7CCDB (common), #59A6FF (rare), #BD73FF (epic), #FFCC40 (legendary); hero colours hair #23232F, skin #DCCBC4, tee #41558F, jeans #2E3A5F, bag #232B49, sneakers #F2F4FA.
- Transparent background (alpha PNG) for every sprite. No baked ground shadow, no text, no watermark, no frame.
- Readable silhouette at the smallest size. Friendly, bright-on-dark mood. No magic or violet energy effects anywhere: attacks are only a floating weapon swinging.
- Keep the SAME pixel size and the same drawing style in every asset so they look like one game.

OUTPUT FORMAT (important): draw each asset at its native pixel size listed below, then export it enlarged by an exact whole number (8x, nearest-neighbour) so each art pixel is a clean 8x8 block. If you cannot produce exact sizes, make the image large with a clearly visible uniform pixel grid; do not smooth it.

## ASSET LIST (native size in pixels, width x height)

1. Hero, front, standing (77x131). Then a turnaround: front, three-quarter, side facing right, back three-quarter, back (about 40x90 each). Then four face expressions: neutral, happy, surprised, calm eyes closed (about 49x55). Then 5 idle frames (about 38x78). Then a run cycle, 5 frames each for right, left, up, down (about 40x56, same canvas for every frame, feet on the same baseline).
2. Hats, each centred, flat seat line on the bottom edge, 62x52: plain cap (common, grey), chef hat (common, white), propeller beanie (rare, blue with yellow band, coral and teal blades), wizard hat (epic, purple with gold star and gold band), golden crown (legendary, gold with red, blue and green gems).
3. Floating weapons, pointing right, handle at the left, 66x24, one shared sword silhouette: wooden sword (common), ice blade (rare, blue), crystal saber (epic, purple, gold guard, red gem), sun blade (legendary, gold, white sparkle on the pommel).
4. Topic towers, 38x50: stone pedestal with a round coloured cap carrying a code glyph in the outline colour: ";" (yellow F2C14E), "x" (blue 5AA9E6), "if" (purple B48AF2), "f()" (teal 4FD1B0), "[ ]" (green 86D95C), "*p" (coral F2796B).
5. Projectile: a round coral orb with a white "?" (24x24), the same orb frozen in pale ice blue (24x24), and a white four-point impact spark (28x28).
6. Stage maps, top-down, 480x264 (20 columns x 11 rows, 24 px cells), ground plus path plus scenery only, NO towers and NO hero (they are placed by code). Tan path, lighter than the ground, with a darker 1 px edge; trees, rocks, a small white lab building with a teal roof where the path ends. Path corners in (column,row): Stage 1 (0,8) (11,8) (11,6) (8,6) (8,3) (13,3) (13,0) with teal-green ground. Stage 2 (0,6) (7,6) (7,2) (12,2) (12,6) (16,6) (16,9) with meadow green ground. Stage 3 (0,3) (6,3) (6,8) (10,8) (10,2) (14,2) (14,6) (19,6) with cool grey-blue ground.
7. Screen backgrounds, 480x270, calm and low contrast so UI stays readable: (a) intro/login: dusk pine forest, moon, fireflies, a glowing stone code tower in the centre; (b) main menu: night campus, lit windows, the lab in the centre, lamp posts; (c) professor screens: quiet dark slate with a faint grid and empty panels, no mascot.
8. Logo: the word CODEQUEST in chunky pixel capitals, "CODE" teal and "QUEST" gold with a darker bottom tone and a 1 px outline plus drop shadow (about 167x31), and a square mark with teal tile and angle brackets "< >" (24x24).

## ASSET REQUEST TEMPLATE

"Using the master prompt and the attached hero sheet, create asset number [N], [name]. Native size [W]x[H] pixels, exported at 8x with exact nearest-neighbour pixel blocks, transparent background, palette as given. File name [snake_case_name].png."

## NEGATIVE PROMPT

anti-aliased edges, blurry, smooth gradients, 3D render, photorealistic, vector look, mixed pixel sizes, dithering noise, drop shadow, background, text, watermark, frame, violet or magic energy effects, extra limbs, off-model hero, different outfit
