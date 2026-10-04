# Visual Synth Sequencer

## High-Level Architecture Overview

Visual Synth Sequencer is a real-time, interactive 16-step by 8-row desktop audio sequencer and visual synthesizer built in Java. Designed with a modular separation-of-concerns architecture, the system combines low-latency digital signal processing (DSP), hardware-accelerated Swing graphics, synchronized state management, and file persistence.

```
+-----------------------------------------------------------------------+
|                              MainApp                                  |
|         (Top-Level JFrame, Toolbar Controls, Swing BPM Timer)         |
+-------------------+-------------------------------+-------------------+
                    |                               |
                    v                               v
          +-------------------+           +-------------------+
          |     GridPanel     |           |  SequencerModel   |
          |  (Graphics2D UI,  |---------->|  (Thread-Safe 8x16|
          |   Playhead Step)  |           |   Matrix State)   |
          +-------------------+           +---------+---------+
                    |                               |
                    |                               v
                    |                     +-------------------+
                    |                     |  PatternStorage   |
                    |                     | (JSON Persistence)|
                    |                     +-------------------+
                    v
          +-------------------+
          |    AudioEngine    |
          |  (DSP Synthesis,  |
          |   Cached Pool)    |
          +-------------------+
```

### Core Architecture Components

1. **Digital Signal Processing (DSP) Audio Engine (`src/audio/`)**
   - Implemented via `javax.sound.sampled` using a 44,100 Hz, 16-bit signed PCM, mono audio stream configuration.
   - Generates programmatic waveforms on the fly without external audio dependencies. Supports pure sinusoidal (`Math.sin`) and bipolar square wave generation.
   - Applies an exponential decay envelope with dynamic attack and release ramps to eliminate click artifacts during voice initialization and release.
   - Employs a dedicated cached thread pool executor (`Executors.newCachedThreadPool`) with daemon worker threads (`AudioEngine-Voice`) to guarantee non-blocking audio dispatch during UI event execution.

2. **Interactive Matrix User Interface (`src/ui/`)**
   - Custom-rendered Swing component extending `JPanel` that utilizes `Graphics2D` with bilinear anti-aliasing and subpixel text antialiasing.
   - Renders a 16-column by 8-row grid with note pitch indicators (`C5` down to `C4`) and 1-indexed step counters.
   - Provides coordinate hit-testing for mouse press events to toggle individual pattern cells.
   - Features real-time playhead rendering that sweeps across steps synchronously with playback ticks, styled using a high-contrast dark synth visual scheme.

3. **Synchronized Model and Persistence Layer (`src/model/`)**
   - Manages a thread-safe 8x16 boolean matrix state guarded by method-level synchronization locks (`synchronized`), ensuring safe concurrent access between the Swing Event Dispatch Thread (EDT), audio worker threads, and timer routines.
   - Maps grid rows 0 through 7 to exact C-major scale frequencies (523.25 Hz down to 261.63 Hz).
   - Provides JSON-based serialization and deserialization via `PatternStorage` to save and restore composition configurations, step matrices, and tempo values with error checking and validation (`PatternFileException`).

4. **Application Orchestration and Timing Loop (`src/app/`)**
   - Serves as the executive control layer integrating UI components, data structures, and audio dispatch routines into a unified Swing `JFrame`.
   - Drives sequence progression using a precision `javax.swing.Timer` scheduler. Step delays are derived from tempo: `interval_ms = (60000 / BPM) / 4` for 16th-note step resolution.
   - Coordinates user interactions from transport buttons (Play, Stop), tempo adjustment sliders (60 to 240 BPM), waveform selection toggles (Sine/Square), and pattern file dialogs.

---

## Project Directory Tree

```
visual-synth-sequencer/
├── samples/
│   ├── arpeggio_cmajor.json
│   └── basic_beat.json
├── src/
│   ├── app/
│   │   └── MainApp.java
│   ├── audio/
│   │   └── AudioEngine.java
│   ├── model/
│   │   ├── PatternFileException.java
│   │   ├── PatternStorage.java
│   │   ├── SequencerModel.java
│   │   └── SequencerModelTest.java
│   └── ui/
│       ├── GridPanel.java
│       ├── UITheme.java
│       ├── index.html
│       └── style.css
├── .gitignore
├── README.md
└── README_MEMBER3.md
```

---

## Team Module Allocation

The codebase is partitioned into distinct sub-packages corresponding to functional roles and engineering ownership:

| Package | Key Source Files | Lead Role | Primary Responsibilities |
| :--- | :--- | :--- | :--- |
| `src/app` | `MainApp.java` | Application Lead & System Integrator | Swing `JFrame` orchestration, control toolbar construction, transport state machine (Play/Stop), BPM timing loop, cross-module thread management, and dialog bindings. |
| `src/audio` | `AudioEngine.java` | Audio Systems Engineer | `javax.sound.sampled` output line management, dynamic PCM byte buffer synthesis, sine and square wave generation, ADSR envelope shaping, and concurrent voice dispatch via worker threads. |
| `src/model` | `SequencerModel.java`<br>`PatternStorage.java`<br>`PatternFileException.java` | Data & Persistence Engineer | 16x8 matrix representation, synchronized cell toggle/query methods, C-major scale frequency mapping, JSON pattern serialization and deserialization, and unit test suites. |
| `src/ui` | `GridPanel.java`<br>`UITheme.java` | UI & Visuals Engineer | Custom `Graphics2D` matrix rendering, coordinate-to-cell hit detection, active note glowing, note pitch row headers (`C5` to `C4`), playhead tracking, and UI palette constants. |

---

## Build and Execution Commands

### Prerequisites
- Java Development Kit (JDK) 11 or higher
- Git 2.30 or higher

### Compilation
From the project root directory, compile all package sources into the output directory `bin/`:

**Linux / macOS (Bash):**
```bash
mkdir -p bin
javac -d bin src/audio/*.java src/model/*.java src/ui/*.java src/app/*.java
```

**Windows (Command Prompt):**
```cmd
if not exist bin mkdir bin
javac -d bin src/audio/*.java src/model/*.java src/ui/*.java src/app/*.java
```

**Windows (PowerShell):**
```powershell
if (!(Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" }
javac -d bin (Get-ChildItem -Recurse -Filter "*.java" src).FullName
```

### Execution
Run the compiled application entry point:

```bash
java -cp bin app.MainApp
```

### Running Model Verification Tests
Execute the standalone data layer test suite:

```bash
java -cp bin model.SequencerModelTest
```

---

## Engineering Workflow Standards

### Branching Model
- **`main` Branch**: Production-ready, stable codebase. Direct commits to `main` are restricted.
- **`feature/*` Branches**: Development branches aligned to module ownership:
  - `feature/audio`: DSP tone synthesis and audio output infrastructure.
  - `feature/ui`: User interface rendering, styling, and visual components.
  - `feature/data`: Model structures, synchronized accessors, and pattern storage.
  - `feature/app`: Top-level window orchestration and system integration.

### Code and Integration Guidelines
1. **Package Encapsulation**: Source files must reside within explicit package namespaces matching directory paths (`package app;`, `package audio;`, `package model;`, `package ui;`).
2. **Interface Contracts**: Cross-module method calls must adhere strictly to agreed public API contracts (e.g., `SequencerModel` synchronized state methods and `AudioEngine.playTone`).
3. **Dry-Run Merge Audits**: Feature branches undergo integration dry-runs on temporary integration branches (e.g., `test-ui-pr-integration`) to verify zero merge conflicts, package consistency, and compilation integrity before merging into `main`.
4. **Commit Hygiene**: Commit messages follow conventional semantic prefixes (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`).
