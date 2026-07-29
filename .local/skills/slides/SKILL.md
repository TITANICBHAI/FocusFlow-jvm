---
name: slides
description: Instructions for building, editing, importing, and exporting slide deck artifacts in the Replit workspace. Use this skill when the user asks for slides, a presentation, a pitch deck, a slide deck, or any slide-based content; when the user attaches or imports a .pptx file; or when the user asks to export, download, or save their slides as PPTX or PDF. For a NEW deck, read this skill BEFORE creating the slides artifact -- it runs the pre-generation questions (deck length, visual style, content outline) and says when to create the artifact. Covers the manifest contract, slide component conventions, visual editing compatibility, PPTX import, PPTX/PDF export, and design guidance for creating presentations.
---

# Slides -- Presentation Decks in Code

This skill drives the slides flow end to end: PPTX import, export, and -- for NEW decks -- the pre-generation questions that must be settled BEFORE the slides artifact is created. The build-time rules live in `./references/building.md` and are injected automatically when the artifact is created.

## PPTX Import -- Handle First

**Trigger:** any attachment ending in `.pptx`, or a request to import/convert/open an existing presentation. No interpretation -- `.pptx` attached means import.

**First action:** call `importPptx({ filePath: "attached_assets/<filename>.pptx" })` as your very first tool call. One call per `.pptx` if multiple are attached. If no slides artifact exists, scaffold a bare one as the destination first -- no template styling, no sample slides -- then import.

**Do NOT, before importing:** ask clarifying questions, run brand research (`extractBranding`/`webFetch`/`imageSearch`/`webSearch`), generate images, apply a template, or write any slide JSX from scratch. Mixed requests ("import this and tweak X") still import first, then handle the edit on the imported slides.

The goal is near 1:1 fidelity -- the import returns finished components; do not redesign them. Adaptation ("use this as inspiration") is the only exception and follows the path in `./references/importing.md`.

References:

- ./references/importing.md -- read this **before** doing anything else with the imported files. Staging-directory layout, the full copy / normalize / manifest workflow, and the "adaptation instead of 1:1 import" exception.

## Exporting Slides (PPTX, PDF)

When the user asks to export, download, or share their slides as a PPTX/PDF or otherwise requests a downloadable file, call `exportSlides({ format: "pptx" | "pdf", presentationName?, artifactDirName? })`. A request to save the deck to the workspace, as a template, or for future reuse is NOT an export; follow "Saving a Deck as a Workspace Template" below instead. On export `success`, hand `result.filePath` to `presentAsset` with a clean human-readable title (e.g. `"My Deck (PDF)"`) -- that is what produces the chat card and registers the file in the Library. Never tell the user to "click the export button" or "download from the preview pane" as a substitute for actually producing the file.

References:

- ./references/exporting.md -- read this **before** calling `exportSlides`. Full callback interface, the `presentAsset` example, the Google Slides redirect, and internal-only implementation notes.
- ./references/export-failures.md -- read this **only when** `exportSlides` returns `success: false`, or when the user reports a failed UI-button export. Per-`errorCode` remedy table, two-attempt cap, and the reproduce-and-diagnose pattern.

## Saving a Deck as a Workspace Template

When the user asks to save the deck as a template (to their workspace, "for the team", or for reuse on future decks), call the `saveArtifactAsTemplate` callback with the slides artifact -- see the `artifact-templates` skill for the full interface and error handling. Never claim the template is saved without calling it; there is no other save path from chat. Saving is asynchronous: on success report that publishing has *started*, not that it is saved. If the result is `NOT_AUTHORIZED`, explain the permission problem in your own words without quoting the raw message or retrying. This is distinct from exporting: export produces a PPTX/PDF file, saving a template makes the deck's style reusable from the theme picker.

## Template Selection and Pre-Generation Flow

When the user asks for a NEW slide deck, run this flow BEFORE creating the slides artifact. Ask the user for any direction they haven't given before doing anything else. Do not scaffold, research, outline, or write files first -- `createArtifact` comes only at the end of this flow (see "Create the artifact only when ready to build"). This is required.

First judge what the user already gave -- length and style independently, having one doesn't let you skip the other. A topic, audience, or subject is not a length or a style, and don't pick a slide count yourself to skip a question. A detailed outline settles length and content but not style. Then ask for exactly what's missing, all in a single turn:

- **Both length and style missing** -- call `requestSlideStyleDirection` with `askDeckLength: true`. One form asks both: deck length (Brief 3-6 / Standard 7-12 / Exhaustive 15-20, plus an "Exact number of slides" box) and the visual-theme picker. Do NOT also call `AskQuestion` for length -- the combined form covers it.

  ```js
  await requestSlideStyleDirection({
    slidesTopic: "A pitch deck for a fintech startup",
    askDeckLength: true,
  });
  ```
- **Only style missing** -- call `requestSlideStyleDirection` with just `slidesTopic` (omit `askDeckLength`): a short sentence naming the deck's subject; it ranks visual templates and shows the user a picker.
- **Only length missing** -- ask with the generic `AskQuestion` model tool. Call `AskQuestion` directly as a tool -- it is NOT a CodeExecution callback, so never `await` it inside a code block. Use a single `singleSelect` field with exactly these options (the auto-attached "Additional comments" box collects an exact count if they want one):
  - `Brief (3-6 slides)`
  - `Standard (7-12 slides)`
  - `Exhaustive (15-20 slides)`

  Pass these `AskQuestion` arguments:

  ```json
  {
    "question": "How long should the deck be?",
    "fields": [
      {
        "kind": "singleSelect",
        "name": "deckLength",
        "title": "Deck length",
        "required": true,
        "options": [
          { "value": "brief", "label": "Brief (3-6 slides)" },
          { "value": "standard", "label": "Standard (7-12 slides)" },
          { "value": "exhaustive", "label": "Exhaustive (15-20 slides)" }
        ],
        "comment": { "name": "customSlideCount", "title": "Exact number of slides (optional)" }
      }
    ]
  }
  ```
- **Neither missing** -- ask nothing and move on.

Ask everything you need in one turn: when both length and style are missing, the single combined form covers both -- never split them into two back-to-back questions. Wait for the user's answer before continuing. The content outline review is always its own later step; never bundle it with these questions. Don't outline, create the artifact, or build until both length and style are known. Skip these questions only for edits to existing decks, imports/conversions, or when the user asks to skip the questions.

### Using the Selected Template

This section is about *what* to match, not *when* to start. The order of operations for new decks lives in `<first_build>` -- follow that sequence. Do not begin writing slide files from this section; the Content Outline Review still has to happen first.

The picker also offers the user's own saved workspace templates alongside the built-in catalog. If the user picks one, the response identifies it by `workspace_template_slug` -- call `useArtifactTemplate` with that slug to materialize it and follow the returned instructions instead of steps 1-2 below (steps 3-4 still apply, using the materialized template as the reference).

1. **Study the template preview** -- For a selected template, a preview image is injected into your context after the user picks one. This is your primary visual target -- match it as closely as possible.
2. **Read the reference file** -- Read `templates/<template-id>.md` (relative to the skill file) for exact hex codes, font choices, layout details, source code for all 4 slides, and design patterns.
3. **Plan Slide 1 fidelity first** -- When you do build (after the Content Outline Review in `<first_build>` step 3), write Slide 1 first to match the reference image as closely as possible. Take a screenshot and compare against the reference image to verify fidelity before extending the patterns to the remaining slides.
4. **Extend patterns to the rest of the deck** -- Maintain consistent styling throughout the deck guided by both the reference images and the text description. Templates only ship with ~4 sample slides and their images, so for any additional slides you'll need to fill in the gaps yourself: source or generate fitting images via `imageSearch` or the `media-generation` skill, and extend the template's layout patterns to cover the remaining content.

The preview image is the ground truth. The text description and source code supplement it with precise values. Follow both as your creative direction, then adapt to the specific content.

When the user selected an "Auto" option, no preview image is injected. Follow the standard planning process in `./references/building.md` (`<planning>`) to develop an original creative direction.

### Required: Content Outline Review (new decks only)

For every new slide deck, call `proposeSlideContent` with a concrete content outline and wait for the user's confirmation before writing any slide files, unless one of the skip cases below applies. A short prompt like "Build me slides about dogs", "a deck about coffee", or "pitch deck for a fintech" is not a skip case -- that's exactly when the outline matters most, since the user hasn't told you what should be on each slide yet.

The outline IS the content of the deck, slide by slide -- not a summary of it. Each entry must carry the **exact** copy that will appear on that slide: the real `headline` the slide will show, and a `body` containing the actual bullets, stats, and supporting lines for that slide. The user should be able to read the outline top-to-bottom and see precisely what every slide will say. Do not send slide titles only, vague descriptions ("intro slide", "overview of the problem"), or meta-notes about what you plan to put there -- write the literal slide copy.

Format the `body` as a clean, readable list: one bullet per line, each starting with `- `, so it renders as a tidy bullet list. Keep bullets to short phrases or fragments, the way they will read on the slide. For a title or section slide whose body is a single subtitle line, a plain line without a bullet is fine. Do not cram a whole paragraph into one bullet.

Images can make decks feel much more produced, especially creative or personal decks. When a searched or generated image would make a slide stronger, include a brief `Image:` note as its own line in that slide's body describing what the image should show and how it fits the slide.

Send the outline through the callback. Include the actual text content that will appear on each slide, plus any `Image:` notes for searched or generated imagery.

```js
await proposeSlideContent({
  prompt:
    "Here's a draft outline for your deck. Edit anything you'd like, then confirm.",
  slides: [
    {
      headline: "Acme Analytics",
      body: "The fastest way to turn raw events into decisions.",
    },
    {
      headline: "The problem",
      body: "- Teams lose hours stitching dashboards together\n- Insights arrive a week too late to act on\n- Every new question means another data-team ticket\nImage: a cluttered, overwhelming legacy dashboard",
    },
    {
      headline: "Traction",
      body: "- 3x revenue growth over the last two quarters\n- 92% logo retention\n- 40+ teams live in production",
    },
  ],
});
```

**Research first when the deck depends on real facts.** If the deck is about a real company, real product, real industry, or any subject where the slides need verifiable claims (revenue, headcount, market data, product specifics, dates), complete brand research and content/fact verification *before* drafting the outline -- see `<first_build>` steps 1--2 (full research guidance: `./references/building.md`, `<planning>` steps 1--2). Once the user confirms the outline, the "User-supplied copy is canonical" rule treats it as verbatim source material, so guessed facts get locked in. **Do not fabricate stats, revenue numbers, dates, or company specifics in the outline.** If you cannot verify a figure, omit it or label it explicitly as a placeholder (e.g. "[stat to verify]"). For purely topical or creative decks ("dogs", "coffee", "birthday party") with no verifiable claims, draft the outline after studying the template.

Wait for the user's response. If they request changes, incorporate them. Then create the artifact and build (see `<first_build>` below).

### Skip conditions

Skip the content outline review and proceed directly to building only when one of these is true:

- The user is asking to edit/modify an existing deck, not create a new one.
- The user is importing/converting from an existing file (PPTX, PDF, etc.) -- the source file already defines the content and structure. For `.pptx`, the "Handle First" section is required, not optional.
- The user already supplied per-slide content -- a numbered or bulleted slide-by-slide outline ("Slide 1: Title -- X. Slide 2: Problem -- Y--"), an attached script / talking-points / Google Doc that names what each slide should say, or copy in the prompt that maps cleanly onto specific slides. A topic, a brand, an audience, or a slide count alone is not per-slide content.
- The user explicitly opts out ("just build it", "skip the outline", "no questions, please").

Don't skip just because:

- The prompt is short.
- The user gave you a topic but not per-slide content ("a deck about coffee", "pitch deck for a fintech").
- The user gave you a slide count but not per-slide content ("make me 8 slides on X").
- You feel confident you can fill in sensible defaults -- that's exactly when the outline review is most valuable.

If you're unsure whether the user supplied "enough", err toward running the outline review -- it costs one round-trip and prevents rebuilding a deck that misses the user's intent.

### Create the artifact only when ready to build

For a NEW deck, do not call `createArtifact` while any pre-generation question is unresolved. Create the slides artifact only once deck length, visual style, and the content outline are all settled -- each either answered by the user, already given, or skipped per its skip conditions. If the user opted out of all questions ("just build it"), create it right away.

Call `createArtifact({ artifactType: "slides", ... })` as described in the `artifacts` skill. Creating the artifact scaffolds the deck, registers its workflow, and injects the build reference (`./references/building.md`) plus the scaffold's key files into your context -- do not re-read them after creating.

<clarifying_questions>
**If a `.pptx` is attached, do not ask anything -- go straight to `importPptx` per "PPTX Import -- Handle First".**

Deck length and visual style are governed only by "Template Selection and Pre-Generation Flow" -- including its edit/import/opt-out skips. When either is missing for a new deck, ask with the matching mechanism there (both missing: `requestSlideStyleDirection` with `askDeckLength: true`; only style: `requestSlideStyleDirection`; only length: `AskQuestion`). Do not apply the ambiguity test below to length or style.

This section governs only short clarifying questions other than deck length and visual style. For those other details, do not re-ask what the user already gave:

- **Audience** ("for the board", "for a sales pitch", "internal team")
- **Tone** ("playful", "corporate", "editorial")
- **Brand or company** (named company, attached logo, linked website)
- **Content topic** (the deck subject -- explicit topic vs. vague hand-wave)

Ask about these only if genuinely ambiguous and the answer would materially change the deck. Otherwise use sensible defaults -- infer audience from topic, and commit to an aesthetic that matches the subject.

This section governs only short clarifying Q&A. It doesn't override the Content Outline Review under "Template Selection and Pre-Generation Flow" -- that step still runs on every new deck unless one of the Skip conditions there applies.
</clarifying_questions>

<first_build>
When building a new slide deck for the first time, follow this exact sequence. Steps 0--3 happen BEFORE the slides artifact exists -- do not create it early:

0. **Resolve pre-generation direction** -- run "Template Selection and Pre-Generation Flow" before outlining or building; it is the source of truth for missing length/style, the ask mechanism for each combination of missing details, and the skip conditions.
1. **Research brand** (real companies only): **Skip this step entirely if the user already supplied the brand source of truth** -- attached brand guide, exact hex codes, approved fonts, design system file, sibling artifact CSS, etc. Use what they gave you. Otherwise, for real companies, prefer the Firecrawl-backed tools -- they return real, structured data. The order is: `extractBranding` -- `webFetch` on official pages -- `webSearch` only as a last resort.
   - Start with `extractBranding` on the official site for colors, fonts, and visual identity.
   - Use `webFetch` on the homepage, about page, or key product pages for real company copy and positioning.
   - Only fall back to `webSearch` if neither tool can reach the site (e.g. the company has no public site, or you genuinely cannot find the URL). Do NOT default to `webSearch` for brand colors, fonts, or positioning -- search snippets are noisy and miss the real brand tokens that `extractBranding` returns.
   - `extractBranding` already returns the company's logo image alongside colors and fonts -- use that logo when it's good. `imageSearch` via the `image-search` skill is a useful complement: reach for it when `extractBranding`'s logo is missing or low-quality, when the company has no site Firecrawl can reach, or when you want a cleaner reference image. For non-brand real-world imagery (product shots, team photos, venues), defer to the build phase (step 5 below and the image-sourcing guidance in `./references/building.md`) -- don't stall the first build crawling for those.
   - If the visual feel of the source site matters, use external-URL `screenshot` for quick visual reference.
2. **Verify content** (real-company / data-driven decks only): If the deck will make verifiable claims (real revenue, headcount, market data, product facts, dates), gather those facts now. Lead with `webFetch` on the company's own pages, then `webSearch` only for facts not on the company's site (batch concurrent queries). The full research guidance lives in `./references/building.md` (`<planning>` steps 1--2). Skip this step for purely topical or creative decks ("a deck about dogs", "birthday party deck") where there are no verifiable claims to research. **Do not fabricate stats, revenue numbers, dates, or company specifics.** Anything you cannot verify must be omitted from the outline draft (or marked as a placeholder) -- once the outline is confirmed, that copy is canonical.
3. **Run the Content Outline Review** -- only after length and style are known, call `proposeSlideContent` with a concise outline, using only verified facts from steps 1--2, and wait for the user's response before any of the steps below. This reviews content only; never use it to ask for length or style. Skip only for the cases listed in "Skip conditions" under "Template Selection and Pre-Generation Flow".
4. **Create the slides artifact** -- see "Create the artifact only when ready to build" above. This is the first step that touches the workspace; everything before it was questions and research.
5. **Generate images** (if needed): Use `generateImage` via the media-generation skill.
6. **Write ALL files in a single parallel batch.** `index.html`, `index.css`, every slide `.tsx` file, and `slides-manifest.json` are all independent -- write them ALL in one parallel tool call. Do not write them sequentially.
   - `index.html`: Update Google Fonts links for your chosen display + body fonts.
   - `index.css`: Fill in CSS variables in `:root` with brand palette and font families. Use the `@theme inline` tokens -- write `text-primary`, `bg-accent`, `font-display` in Tailwind classes instead of inline styles.
   - Each slide `.tsx` file in `src/pages/slides/`
   - `slides-manifest.json` with all entries
7. **Run validation**: `pnpm run --filter @workspace/<slug> validate-slides`
8. **Restart workflow**.
9. **Take the cover screenshot** -- one `screenshot` call with `source: { "type": "appPreview", "artifactDirName": "<deck dir>", "path": "/slide1" }`. This screenshot becomes the deck's cover image on the home screen and on saved templates -- without it the deck shows a generic placeholder icon -- and it confirms the deck actually renders (no blank screen, no error overlay). Do not screenshot the other slides. Take this shot after validation and the visual QA pass are complete; if slide 1 or the deck-wide theme changes afterward for any reason (a defect the shot itself reveals, a late repair), retake it after the fix -- the stored cover is always the newest screenshot.

Do NOT restart workflow until all slides are written. Do NOT read files you just scaffolded -- they are already in your context.
A quick seamless build is what you are aiming for. If the user gave you an exact slide count (in their prompt, or the "Exact number of slides" box on the `AskQuestion` deck-length form or the combined `requestSlideStyleDirection` form), use that exact number. If they picked a length range, pick a slide count that feels right inside that range. If the length question was skipped (opt-out, edit, or import), default to around 6 -- don't go longer unless the user asked for it.
Do not screenshot-iterate during the build -- `validate-slides` is your layout QA loop, and you have two priorities: speed and design. The only screenshots a first build needs are the slide 1 template-fidelity check (when a template reference image exists) and the cover screenshot in step 9.
</first_build>

## Building and editing decks

The build-time rules -- the artifact and manifest contract, the workspace export contract, planning, layout, typography, and the hard constraints every slide must follow -- live in `./references/building.md`. It is injected into your context automatically when the slides artifact is created; do not re-read it then. When editing an existing deck in a later session (or whenever it is genuinely not in your context), read `./references/building.md` before writing any slide files.

After an edit session that changed slide 1 or the deck-wide theme, retake the cover screenshot (step 9 of `<first_build>`) so the deck's cover stays current. If you are unsure the deck ever had a cover (older decks), take one.
