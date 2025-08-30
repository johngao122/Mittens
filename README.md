# Mittens — Knit Dependency Injection Analysis Plugin

Mittens is an IntelliJ IDEA plugin paired with a local Next.js UI that analyzes projects using TikTok’s Knit dependency injection framework. It detects DI issues, exports a dependency graph as JSON, and renders an interactive graph in a web view embedded inside the IDE (JCEF) or in your browser.

- Interactive dependency graph (pan, zoom, focus paths)
- Detection of circular dependencies, ambiguous providers, and qualifier issues
- In‑IDE actions to run analysis and open the graph
- JSON export + live refresh via SSE to the UI

[PLACEHOLDER IMAGE: Overview diagram — IntelliJ plugin ⇄ Next.js UI with dependency graph]


## Project Structure

- `mittens/`: IntelliJ Platform plugin (Kotlin, Gradle, JCEF)
- `view/mittens/`: Next.js 15 app (React 19 RC, Tailwind, D3/VChart) for the graph UI + APIs
- `sample_project/`: Demo Kotlin project wired with the Knit Gradle plugin that produces `build/knit.json`

[PLACEHOLDER IMAGE: IDE screenshot with “Run Knit Analysis” and “Open Knit Dependency Graph” in Tools menu]


## Tools Used

- IntelliJ Platform + Gradle IntelliJ Plugin
  - Kotlin JVM (plugin): 2.0.x
  - Gradle IntelliJ Plugin: 1.17.x
  - Target IDE: IntelliJ IDEA Community 2024.2
  - JCEF for the embedded web view
- Kotlin + Gradle Tooling API
  - Jackson (core, databind, module-kotlin) for JSON serialization
  - Gradle Tooling API for build/task integration
- Next.js 15, React 19 RC
  - D3 + VisActor/VChart for graph rendering
  - TailwindCSS, Radix UI, Jotai
- Knit Gradle Plugin (in `sample_project/`) to generate `build/knit.json`


## Prerequisites

- JDK 17 for building/running the IntelliJ plugin
- IntelliJ IDEA 2024.2+ (Community or Ultimate)
- Node.js 18+ and npm/pnpm for the Next.js UI
- macOS/Linux/Windows supported (JCEF enabled in IDE)


## Quick Start (Recommended)

1) Start the graph UI
- `cd view/mittens`
- `npm install` (or `pnpm install`)
- `npm run dev`
- UI runs at `http://localhost:3000` and expects graph at `/dependency`

2) Run the IntelliJ plugin in a sandbox IDE
- In another terminal:
  - `cd mittens`
  - `./gradlew runIde`
- A sandbox IDE launches with the plugin installed. Open a project that uses Knit (or `sample_project/`).
- In the IDE: Tools → Knit Analysis → Run Knit Analysis
- Then: Tools → Knit Analysis → Open Knit Dependency Graph
  - The embedded web view connects to `http://localhost:3000/dependency`

[PLACEHOLDER IMAGE: Web view showing dependency graph inside IDE]


## Other Ways to Run

- Install the plugin build in your main IDE
  - `cd mittens && ./gradlew buildPlugin`
  - Find the ZIP in `mittens/build/distributions/*.zip`
  - In IntelliJ: Settings → Plugins → gear icon → Install Plugin from Disk → select the ZIP
  - Start the UI: `cd view/mittens && npm run dev`
  - Use Tools → Knit Analysis actions as above

- Use the UI with the included sample project (no IDE plugin required)
  - Start UI: `cd view/mittens && npm run dev`
  - Build + import data: `curl -X POST http://localhost:3000/api/refresh`
    - This runs `./gradlew shadowJarWithKnit` inside `sample_project/`, reads `build/knit.json`, and imports it to the UI.
  - Open `http://localhost:3000/dependency` to explore the graph

- Production‑like UI
  - `cd view/mittens`
  - `npm run build && npm run start`
  - Update the plugin’s web view URL if you serve on a different port/host


## How It Works

- Detection and Analysis (plugin)
  - The plugin checks for Knit usage and, if configured, triggers Gradle tasks (e.g., `shadowJarWithKnit`) via `GradleTaskRunner`.
  - Source and/or build artifacts are analyzed by `KnitAnalysisService`, producing a structured `AnalysisResult`.
  - `GraphExportService` converts results into a graph JSON shape suitable for the UI.

- UI Integration (plugin ⇄ UI)
  - The plugin opens a JCEF browser (`KnitWebViewFileEditor`) pointed at `http://localhost:3000/dependency`.
  - On export, the plugin POSTs JSON to `http://localhost:3000/api/import-data`.
  - The UI stores the latest graph, writes it to `src/data/imported-dependency-graph.json`, and broadcasts an SSE event so open pages refresh.

- UI Rendering (Next.js)
  - The UI reads the imported graph and renders an interactive dependency network (D3/VisActor).
  - API routes:
    - `POST /api/import-data` — accept graph JSON from plugin
    - `GET /api/import-data` — return latest imported graph
    - `POST /api/refresh` — build `sample_project` and import its `knit.json`

[PLACEHOLDER IMAGE: Sequence diagram — Run Analysis → Export JSON → UI import → Graph render]


## In‑IDE Usage

- Run Analysis: Tools → Knit Analysis → Run Knit Analysis (or shortcut `Ctrl+Alt+K`)
- Open Graph: Tools → Knit Analysis → Open Knit Dependency Graph (or `Ctrl+Alt+G`)
- Export Analysis (from the web view toolbar) to a temp JSON file
- Open in Browser (from the web view toolbar) to view outside the IDE

[PLACEHOLDER IMAGE: Notification balloon with summary and “View Full Report”]


## Developing

- Plugin (IntelliJ)
  - `cd mittens`
  - `./gradlew test` — run tests
  - `./gradlew runIde` — launch sandbox IDE
  - `./gradlew buildPlugin` — produce distributable ZIP in `build/distributions`
  - Optional signing/publishing use these env vars: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`

- UI (Next.js)
  - `cd view/mittens`
  - `npm run dev` — dev server at `http://localhost:3000`
  - `npm run build && npm run start` — production build

- Sample Project
  - `cd sample_project`
  - `./gradlew shadowJarWithKnit` — generates `build/knit.json`


## Troubleshooting

- Web view shows “Web view is not available”
  - Start the UI: `cd view/mittens && npm run dev` (default port 3000)
  - Then retry in the IDE using the “Retry Connection” button or reopen the graph

- No Knit tasks found / Gradle errors
  - Ensure your project applies the Knit Gradle plugin and defines `shadowJarWithKnit`
  - Verify Gradle wrapper exists and runs on your machine

- Graph not updating in UI
  - Confirm the plugin POSTed to `/api/import-data` successfully (check the IDE’s event log)
  - Ensure the Next.js server logs show “Data imported successfully”

- Port conflicts
  - The plugin expects `http://localhost:3000`. If you change the UI port, update the plugin’s web view URL in `KnitWebViewFileEditor`.


## Screenshots To Add

- [PLACEHOLDER IMAGE: Tools menu with Knit actions]
- [PLACEHOLDER IMAGE: Dependency graph view (IDE tab)]
- [PLACEHOLDER IMAGE: Full report dialog and analysis tips]
- [PLACEHOLDER IMAGE: Next.js page in browser]


## License

[Add your license here]

