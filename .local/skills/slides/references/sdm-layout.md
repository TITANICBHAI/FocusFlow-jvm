# SDM layout: deterministic 1920x1080 authoring

Use this reference for every new SDM JSON deck, especially when you cannot open
the app or take screenshots. The validator is the visual backstop: it estimates
text wrapping, rejects unreadable autofit, catches text-to-text collisions, and
checks canvas bounds.

## Coordinate contract

- Canvas is exactly `1920x1080`.
- Use `x=80..1840`, `y=80..1000` as the recommended safe rectangle for
  ordinary content. Intentional edge text such as footers and page numbers may
  use the margins, and full-bleed images or decorative shapes may extend beyond
  it.
- Frames use canvas units. Text and stroke sizes use PowerPoint points, and
  `1pt = 2` canvas units (`SDM_POINT_TO_UNIT`).
- Conversion from legacy viewport layouts: `1vw = 19.2` canvas units,
  `1vh = 10.8` canvas units, and `Nvw` text is approximately `N * 9.6pt`.
- Array order is back-to-front paint order. Put a background shape first and its
  separate text element afterward. Shape/text overlap is intentional;
  text/text overlap is almost always a bug.

## Point-size system

| Role | Recommended size |
| --- | --- |
| Hero headline | 48-68pt |
| Slide headline | 30-44pt |
| Subheading | 22-30pt |
| Body | 19-24pt |
| Caption / footnote | 14.4-17pt |
| Hero statistic | 76-112pt |

Every non-empty run must explicitly set `font`, `sizePt`, and `color`. Every
paragraph must explicitly set `lineHeight`: use at least `1.05` for display text
and `1.2` for body text. Do not rely on inherited artifact CSS.

The validator uses 18pt when `sizePt` is omitted; explicit smaller sizes reduce
estimated text dimensions. Do not use undersized text to satisfy validation:
keep runs within the role guidance above and fix the geometry or copy instead.

## Text-capacity budget

Use these conservative estimates before writing a frame:

```text
lineHeightUnits = 2 * sizePt * lineHeight
averageCharacterWidthUnits = 1.2 * sizePt
charactersPerLine = availableWidth / averageCharacterWidthUnits
safeLines = floor(availableHeight / lineHeightUnits * 0.85)
```

`availableWidth` and `availableHeight` exclude text insets (`insetsPt * 2`).
The 85% factor leaves room for wide glyphs, mixed weights, and font-metric
differences. Example: 24pt body copy at 1.2 line height needs about 58 canvas
units per line. A 1200-unit-wide frame holds about 41 average characters per
line; a 300-unit-high frame safely holds four lines, not five.

`letterSpacingPt` is charged per glyph, spaces included: add
`2 * letterSpacingPt` canvas units per character to the width budget. Tracked
headings overflow much earlier than their character count suggests.

Autofit is an emergency guard, not a layout strategy. The validator rejects
text that needs more than about 10% shrink. Fix it by widening/tallening the
frame, shortening the copy, or splitting the slide. Never solve dense content
by shrinking body text below 19pt. `autofit: "resize"` is not implemented by
rendering — the frame will not grow, and the validator rejects overflowing
resize text (`text-autofit-resize`); use `shrink` (the default) or author a
bigger frame.

## Validator issue codes

`validate-slides` reports SDM issues as
`<filepath> [<code>] (<elementIds>): <message>`. Table-cell owners use 0-based
ids like `tbl:r0c0` (row 0, declared cell 0 of table `tbl`). The `c` suffix is
the index in that row's `cells` array, not the occupied grid column after spans.
Treat every code as a build error:

| Code | Meaning | Fix |
| --- | --- | --- |
| `invalid-id` | SDM slide ID contains characters the flat loader cannot resolve | Use only letters, numbers, underscores, and hyphens |
| `manifest-path` | SDM entry's `filepath` is not `src/data/slides/<id>.sdm.json` | Rename the file/entry to the exact convention |
| `missing-file` | Manifest references a document that does not exist | Create the file or remove the entry |
| `parse-json` | File is not valid JSON | Fix the syntax error in the message |
| `schema-invalid` | Document violates the frozen schema, or has duplicate element ids / references to missing assets | Follow the JSON path in the message |
| `unsupported-version` | Document `version` is newer than this tooling | Do not edit the file by hand; regenerate it |
| `canvas-size` | Root `size` is not 1920x1080 | Set `size` to `{ "width": 1920, "height": 1080 }` |
| `table-span` | A cell's `colSpan`/`rowSpan` exceeds the grid or crosses an occupied cell | Reduce the overlapping spans or add columns/rows |
| `text-autofit` | Text needs more than ~10% shrink (or overflows with autofit `none`) | Grow the frame, shorten the copy, or split the slide |
| `text-autofit-resize` | Overflowing text uses unimplemented `resize` autofit | Switch to `shrink` or grow the frame |
| `text-out-of-bounds` | Rendered lines leave the canvas, or the visible region of a `clip: true` group | Move/resize the frame so every line stays visible |
| `text-overlap` | Two elements' rendered text lines intersect | Move or resize the frames apart |
| `widget-module` | A widget references a module missing from `src/widgets/` | Add the file or fix the `module` path |
| `orphan-file` | A `.sdm.json` file has no manifest entry | Add the entry or delete the file |

Rotated (`rotationDeg`) and flipped (`flipH`/`flipV`) text is exempt from the
bounds and overlap checks — the line model cannot represent those transforms —
but autofit still applies. Do not use rotation or flips to silence a collision;
the deck will still render overlapped.

## Required coordinate worksheet

Before writing each `.sdm.json`, make an internal worksheet for every element:

```text
id | type | x | y | width | height | text lines / purpose
```

Then perform these checks numerically:

1. Keep every text frame inside the canvas: `x >= 0`, `y >= 0`,
   `x + width <= 1920`, `y + height <= 1080`. Prefer the safe rectangle for
   ordinary text; reserve the margins for intentional footers or page numbers.
2. Estimate each text frame's characters-per-line and safe line count.
3. Compare every pair of text elements. Their occupied line regions must not
   intersect. Do not merely compare the whole frames: top- and bottom-aligned
   text can share a larger frame without their rendered lines touching.
4. Verify intentional layering order: background/image/shape first, text later.
5. Keep at least 32 units between independent text regions; prefer 48-80.

## Reliable no-browser workflow

1. Choose one layout pattern per slide (hero, split, grid, timeline, etc.).
2. Build the coordinate worksheet and text-capacity budget.
3. Write all SDM JSON and the manifest. Every SDM entry must include
   `"kind": "sdm"`; use an ID containing only letters, numbers, underscores,
   and hyphens, with the exact filepath `src/data/slides/<id>.sdm.json`.
4. Run `pnpm run --filter @workspace/<slug> validate-slides`.
5. Treat every layout diagnostic as a build error. Use the named element IDs and
   suggested repair to update frames/copy, then rerun until validation passes.
6. If browser testing is available, visually inspect afterward. It is extra QA,
   not a substitute for the coordinate and validator passes.

## Renderer-safe authoring subset

For the no-browser path, prefer text, images including percent crop, lines
including dash/cap/arrowheads, paint and gradient-stop opacity, and simple
tables. Renderer-safe shape presets are `rect`, `roundRect`, `ellipse`,
`triangle`, `rtTriangle`, `diamond`, `parallelogram`, `trapezoid`, `chevron`,
`homePlate`, `rightArrow`, `leftArrow`, `upArrow`, `downArrow`,
`leftRightArrow`, `pentagon`, `hexagon`, `octagon`, `plus`, and `star5`.
`chevron` supports an optional `adjustments.depth` from 0 to 0.5. Unknown
preset names render as rectangles; use `geometry.kind: "path"` when the shape
needs different geometry. Avoid rotated or flipped text, deeply nested groups,
and text-heavy widgets unless the user's request requires them — the validator
cannot check transformed text, and export fidelity for those features is not
yet proven.

Do not use `overflow: "visible"` for ordinary text. Use `autofit: "shrink"` or
omit it (shrink is the renderer default), but author the frame so validation
reports a fit of at least 90%.
