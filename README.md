# The Archive // APEX Media Vault & Operations Console

> A privacy-centric, local-first personal media engine and digital "war room" for cataloging, harvesting, reading, and synchronizing Manga, Anime, Music, and Video Games.

---

## 1. System Architecture

```
 +-----------------------------------------------------------------------+
 |                     APEX CONSOLE (Frontend)                           |
 |  React 18  *  Dockview Canvas  *  Tailwind CSS  *  xterm.js Terminal  |
 |  Modes: Standalone Desktop (Electron)  *  Web Browser (Vite / Docker) |
 +-----------------------------------+-----------------------------------+
                                     |
                       HTTP / WebSocket STOMP (61069)
                                     |
 +-----------------------------------v-----------------------------------+
 |                   THE ARCHIVE ENGINE (Backend)                        |
 |            Java 21  *  Spring Boot 4  *  Project Loom                 |
 +------------------+--------------------------------+-------------------+
                    |                                |
         [ Dual-Engine Scraper ]             [ Persistence Tier ]
    +---------------+----------------+       +---------+----------+
    | "The Scout"   | "The Harvester"|       | SQLite  | Postgres |
    | Jsoup         | Selenium       |       | (Vault) | (Shared) |
    | Fast DOM      | Virtual Threads|       +---------+----------+
    +---------------+----------------+
```

---

## 2. Core Capabilities

* **Local-First Media Vault**: Centralized catalog supporting Manga, Anime, Albums/Tracks, and Video Games with custom mod lists and play statuses.
* **Dual-Engine Web Scraper**:
  * **"The Scout" (`JsoupScraper`)**: Fast, lightweight metadata and chapter catalog discovery.
  * **"The Harvester" (`SeleniumHarvester`)**: Headless Chromium automation with Java 21 Virtual Threads (Project Loom) for high-speed, parallel image page downloading.
* **Interactive Visual DOM Inspector**: Embedded proxy tool allowing point-and-click selection of target web elements to automatically generate and bind CSS selectors for site recipes.
* **Built-in Manga Reader & CBZ Archiver**:
  * High-performance canvas-style reader supporting both vertical webtoon scrolling and single-page navigation.
  * Fullscreen mode (`F11`), zoom controls, page jumps, and reading progress bookmarks.
  * Single-click CBZ (Comic Book Zip) packaging and direct OS reader launching.
* **Self-Healing SQLite Database**: Automated startup column verification and patching ensuring zero database schema drift across updates.
* **Dual Deployment Modes**: Run as a native frameless desktop application with Electron or as a headless containerized server via Docker.

---

## 3. Quick Start & Execution

### Option A: Docker Compose (Recommended Turnkey Deployment)
Runs the entire stack (React UI, Spring Boot engine, and headless Chromium) in a single container with persistent volume storage for your media vault and SQLite database:

```bash
docker compose up -d --build
```
Access the APEX Console in your web browser at **`http://localhost:61069`**.

To view logs or stop:
```bash
docker compose logs -f
docker compose down
```

---

### Option B: Standalone Desktop App (Electron)
Runs The Archive as a native, hardware-accelerated desktop application window:

1. **Start the Backend**:
   ```powershell
   # Windows
   .\gradlew.bat bootRun

   # Linux / macOS
   ./gradlew bootRun
   ```

2. **Launch Electron Desktop**:
   ```bash
   cd frontend
   npm run desktop
   ```

---

### Option C: Browser Development Mode (Hot Reloading)
For rapid frontend iteration with Vite HMR:

1. **Start Backend**: `.\gradlew.bat bootRun` (runs on `http://localhost:61069`)
2. **Start Frontend Dev Server**:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
3. Open `http://localhost:3000` (Vite reverse-proxies `/api` and `/ws` to the backend).

---

## 4. Key Endpoints & Architecture Map

| Subsystem | Endpoint | Protocol / Method | Description |
| :--- | :--- | :--- | :--- |
| **Scraper** | `/api/scraper/scout` | `POST` | Fast chapter discovery via Jsoup |
| **Scraper** | `/api/scraper/harvest/{chapterId}` | `POST` | Parallel image harvesting via Selenium |
| **Inspector**| `/api/scraper/live-inspect-proxy?url=...` | `GET` | Proxied webpage with click-interception script |
| **Inspector**| `/api/scraper/test-selector` | `POST` | Live CSS selector verification |
| **Manga** | `/api/manga` | `GET`, `POST` | Manga library catalog |
| **Reader** | `/api/manga/chapters/{id}/pages` | `GET` | Chapter image list (natural order sorted) |
| **Reader** | `/api/manga/chapters/{id}/pages/{file}` | `GET` | Raw image stream |
| **Reader** | `/api/manga/chapters/{id}/package-cbz` | `POST` | Packages chapter into `.cbz` archive |
| **Telemetry**| `/ws` &rarr; `/topic/scraper.progress` | `WebSocket STOMP` | Live chapter download progress bar updates |
| **Workspaces**| `/api/ui/layout` | `GET`, `POST` | Dockview multi-screen workspace presets |

---

## 5. Technology Stack

* **Backend**: Java 21 (LTS), Spring Boot 4, Project Loom (Virtual Threads), Spring Data JPA, Hibernate, SQLite JDBC, PostgreSQL, Jsoup, Selenium WebDriver 4.
* **Frontend**: React 18, TypeScript 5, Vite 6, Tailwind CSS, Dockview, Lucide Icons, xterm.js, Electron 33.
* **Deployment**: Multi-Stage Dockerfile, Docker Compose, Dumb-Init, Eclipse Temurin 21.

---

## 6. Project Documentation Tree

Comprehensive technical deep-dives are located in the [`projectNotes/`](./projectNotes) folder:
* [`projectNotes/00_domain_architecture/`](./projectNotes/00_domain_architecture): Domain entities, models, and database schemas.
* [`projectNotes/01_scraper_engine/`](./projectNotes/01_scraper_engine): The Scout, The Harvester, Virtual Threads, and the Visual DOM Inspector.
* [`projectNotes/02_workspace_collaboration/`](./projectNotes/02_workspace_collaboration): WebSockets, STOMP protocols, and activity logging.
* [`projectNotes/03_command_center_and_ui/`](./projectNotes/03_command_center_and_ui): APEX Console, Dockview layouts, Electron, and Docker deployment.
* [`projectNotes/the_archive_comprehensive_guide.md`](./projectNotes/the_archive_comprehensive_guide.md): Master technical guide and architectural philosophy.
