# DNA Replicator — Implementation Guide

Practical guidance for contributors working on the **DNA Replicator / Virus Builder Simulator**. Read [ARCHITECTURE.md](ARCHITECTURE.md) for system design and [ROADMAP.md](ROADMAP.md) for delivery phases.

---

## Table of contents

1. [Best practices](#best-practices)
2. [Adding new mechanics](#adding-new-mechanics)
3. [Refactoring ProteinService](#refactoring-proteinservice)
4. [Dependency injection strategy](#dependency-injection-strategy)
5. [JavaFX and threading](#javafx-and-threading)
6. [Configuration](#configuration)
7. [Testing strategy](#testing-strategy)
8. [Cross-platform JavaFX](#cross-platform-javafx)
9. [Persistence guidelines](#persistence-guidelines)

---

## Best practices

### Keep educational value explicit

- Place algorithms in **pure Java classes** under a future `algorithm` package.
- Add Javadoc with the concept name and reference (e.g. `CLRS §15.4 — LCS`).
- In the UI status line or insight panel, name the algorithm the player just triggered.

### Gamification vs scientific accuracy

This is a **toy simulator**, not a bioinformatics pipeline. When simplifying:

- Label simplifications in UI copy (“toy alignment”, “demo fold score”).
- Never imply clinical or real-world predictions.

### Spring Boot on desktop

- Keep `spring.main.web-application-type=none`.
- Use `@Component` / `@Service` for application layers; avoid `@Controller` (no HTTP).
- Prefer **constructor injection**; no field `@Autowired`.
- Register JavaFX-specific beans in `@Configuration` only when necessary.

### Layer rules

| Layer | May depend on | Must not depend on |
|-------|---------------|-------------------|
| `domain` | JDK only | JavaFX, Spring, Jackson |
| `algorithm` | `domain`, JDK | JavaFX, Spring |
| `application` | `domain`, `algorithm`, ports | JavaFX concrete classes |
| `presentation` | `application` ports, JavaFX | Algorithm internals |
| `infrastructure` | `domain`, Jackson, filesystem | JavaFX |

### Code size

- Target **≤ 300 LOC** per class.
- Extract nested types from `ProteinService` to top-level package-private classes.
- One public method per user-facing action on application services.

---

## Adding new mechanics

Use this checklist for every new player-facing feature (e.g. “CRISPR edit”, “vaccine shield”).

### Step 1 — Domain

- Extend or add model types in `model` (future `domain.model`).
- Document state changes on `SimulationSession` (fragments, proteins, virus, level, virology).

### Step 2 — Algorithm (if applicable)

```text
src/main/java/com/xai/dnareplicator/algorithm/<area>/<Name>.java
```

- Pure functions or small classes with no side effects.
- Unit test with fixed inputs and expected outputs.

### Step 3 — Application service

- Add a method on the focused service (`DNAService`, `InfectionEngine`, or a new `XxxApplicationService`).
- Orchestrate: read session → run algorithm → mutate session → call view port.

### Step 4 — Presentation

- One button (or menu item) in `SimulationView`.
- Status message via `viewPort.updateStatus(...)`.
- Optional animation via `JavaFxExecutor.runLater(...)`.

### Step 5 — Wiring

- Handler in `SimulationController` should be **≤ 5 lines** (delegate only).

```java
view.getCrisprButton().setOnAction(e -> dnaService.applyCrisprEdit());
```

### Step 6 — Tests and docs

- Unit test for algorithm.
- Application test with mocked `SimulationViewPort`.
- Row in ARCHITECTURE.md **Mechanics catalog** table.

---

## Refactoring ProteinService

`ProteinService` (~1,200 LOC) is the primary god class. Decompose without changing player-visible behavior in the first pass.

### Target structure

```text
algorithm/protein/
  AminoAcidSplayTree.java          ← SplayTree
  FoldingHmm.java                  ← HMM
  FoldingConstraintSolver.java     ← ConstraintPropagator
  ProteinFoldingHeap.java          ← FibonacciHeap
  KolmogorovFoldingEstimate.java   ← KolmogorovComplexity
  GeneticFoldingStrategy.java
  SimulatedAnnealingStrategy.java
  MctsFoldingEvaluator.java          ← MCTS
  MutationPathwayTree.java         ← MutationTree

domain/protein/
  AminoAcid.java
  ProteinBond.java
  FoldingPathway.java
  FoldingResult.java

application/protein/
  ProteinFoldingOrchestrator.java  ← fold pipeline only
  ProteinFoldingApplicationService.java  ← @Service, FX threading
```

### Class extraction map

| New class | Responsibility | Origin |
|-----------|----------------|--------|
| `AminoAcid` | ID, compressed sequence, energy | `ProteinService.AminoAcid` |
| `ProteinBond` | Bond energy, direction, modular filter | `ProteinService.Bond` |
| `AminoAcidSplayTree` | Splay operations | `ProteinService.SplayTree` |
| `FoldingHmm` | State prediction | `ProteinService.HMM` |
| `FoldingConstraintSolver` | CSP propagation | `ProteinService.ConstraintPropagator` |
| `ProteinFoldingHeap` | Priority queue for bonds | `ProteinService.FibonacciHeap` |
| `KolmogorovFoldingEstimate` | Difficulty estimate | `ProteinService.KolmogorovComplexity` |
| `GeneticFoldingStrategy` | GA population | methods in `foldProteins` |
| `SimulatedAnnealingStrategy` | SA runs | methods in `foldProteins` |
| `MctsFoldingEvaluator` | Pathway scoring | `ProteinService.MCTS` |
| `MutationPathwayTree` | Pathway memory | `ProteinService.MutationTree` |
| `ProteinFoldingOrchestrator` | Runs pipeline, returns `FoldingResult` | core of `foldProteins` |
| `ProteinFoldingApplicationService` | Session + view port + `Task` | replaces public `ProteinService` |

### Suggested refactor sequence

1. **Extract domain types** (`AminoAcid`, `ProteinBond`, `FoldingPathway`) — compile-only change.
2. **Extract one algorithm class** (e.g. `FoldingHmm`) — call from `ProteinService`.
3. **Extract `ProteinFoldingOrchestrator`** — move folding logic, keep `foldProteins` as delegate.
4. **Introduce `ProteinFoldingApplicationService`** — move `Task` / `Platform.runLater`.
5. **Delete inner classes** from original file; remove `ProteinService` when empty.

### Orchestrator interface (sketch)

```java
public final class ProteinFoldingOrchestrator {

    private final FoldingHmm hmm;
    private final GeneticFoldingStrategy genetic;
    // ...

    public FoldingResult fold(Protein protein, String sequence) {
        // Run pipeline; return success flags and scores for UI insight panel
    }
}
```

---

## Dependency injection strategy

### SimulationSession (shared mutable state)

```java
@Component
public class SimulationSession {
    private final List<DNAFragment> dnaFragments = new ArrayList<>();
    private final List<Protein> proteins = new ArrayList<>();
    private Virus virus;
    private final Level level = new Level();
    private final VirologyModel virologyModel = new VirologyModel();

    // getters; package-private or service-level mutation methods
}
```

- `DNAService` and `ProteinFoldingApplicationService` receive `SimulationSession`, not separate lists.
- `InfectionEngine` reads proteins/virus from the same session.

### Configuration beans

```java
@Configuration
@EnableConfigurationProperties(SimulationProperties.class)
public class SimulationConfiguration {

    @Bean
    JavaFxExecutor javaFxExecutor() {
        return new JavaFxExecutor();
    }
}
```

### View port (presentation contract)

Evolve `ViewUpdater` into `SimulationViewPort`:

- Keep UI callback methods.
- No JavaFX types in the interface (use `double` coordinates, `String` labels).
- `SimulationView` implements the port; services depend on the interface only.

Use `@Lazy` on `SimulationView` if a circular dependency appears during migration:

```java
public DNAService(SimulationSession session, @Lazy SimulationViewPort viewPort) { ... }
```

---

## JavaFX and threading

### Rules

1. **All UI mutations** on the JavaFX application thread.
2. **CPU-heavy work** on `Task`, `ExecutorService`, or `CompletableFuture` — never on FX thread.
3. **Never** call `viewPort.updateStatus` from a background thread without `javaFxExecutor.runLater`.

### JavaFxExecutor

```java
@Component
public class JavaFxExecutor {
    public void runLater(Runnable action) {
        Platform.runLater(action);
    }
}
```

Inject into `ProteinFoldingApplicationService` and `InfectionAnimationDriver` instead of calling `Platform` directly.

### AnimationTimer

- Keep timers in presentation or a dedicated `InfectionAnimationDriver`.
- Timer callbacks should call application services for game logic, then update the view port on FX thread.

---

## Configuration

### SimulationProperties

Bind [application.properties](src/main/resources/application.properties):

```java
@ConfigurationProperties(prefix = "dna")
public class SimulationProperties {
    private String fastaExportPath = "resources/dna_export.fasta";
    private String stateFilePath = "resources/virus_state.vrs";
    private int maxFragmentLength = 1000;
    private double alignmentScoreThreshold = 0.8;
    // nested ProteinProperties, InfectionProperties, etc.
}
```

### Migrating from static Config

1. Introduce `SimulationProperties` bean.
2. Inject into services; replace `Config.CONSTANT` references.
3. Mark `Config` as `@Deprecated` delegating to properties, then remove.

### Data directory

Prefer a user-writable directory for saves:

```text
${user.home}/.dna-replicator/dna_export.fasta
${user.home}/.dna-replicator/virus_state.json
```

Create directories on startup if missing.

---

## Testing strategy

### Layout

```text
src/test/java/com/xai/dnareplicator/
  algorithm/dna/LongestCommonSubsequenceTest.java
  algorithm/graph/DijkstraPathfinderTest.java
  model/LevelTest.java
  model/VirologyModelTest.java
  application/dna/DnaSplicingServiceTest.java   # with mocked port
```

### Layer tooling

| Layer | Framework | Example |
|-------|-----------|---------|
| Algorithms | JUnit 5 | LCS of `AGTC` / `GTCA` → `GTC` |
| Domain | JUnit 5 | `Level.advanceLevel()` increases resistance |
| Application | JUnit 5 + Mockito | Splice with 2 fragments → one spliced fragment in session |
| Persistence | JUnit 5 + `@TempDir` | FASTA round-trip |
| JavaFX (optional) | TestFX | Splice button disabled until 2 selected |

### Running tests

```bash
mvn test
mvn verify   # includes compile + tests
```

### CI

Run `mvn verify` on push for at least one OS; add matrix for `windows`, `macos`, `ubuntu` when JavaFX profiles exist.

### What to test first (Phase 0 baseline)

1. LCS alignment length and threshold rejection.
2. `VirologyModel.recordInfection` counts.
3. `Level.advanceLevel` increases `fragmentsRequired`.
4. Merge sort ordering by sequence length (DNA export order).
5. Dijkstra returns non-empty path on a 3-node triangle graph (after extraction).

---

## Cross-platform JavaFX

### Problem

`pom.xml` pins `javafx.platform` to `win`, breaking macOS and Linux builds.

### Solution: OS profiles + optional os-maven-plugin

```xml
<properties>
  <javafx.version>21.0.4</javafx.version>
</properties>

<profiles>
  <profile>
    <id>windows</id>
    <activation><os><family>Windows</family></os></activation>
    <properties><javafx.platform>win</javafx.platform></properties>
  </profile>
  <profile>
    <id>mac</id>
    <activation><os><family>Mac</family></os></activation>
    <properties><javafx.platform>mac</javafx.platform></properties>
  </profile>
  <profile>
    <id>linux</id>
    <activation><os><family>Unix</family></os></activation>
    <properties><javafx.platform>linux</javafx.platform></properties>
  </profile>
</profiles>
```

Run with explicit profile if needed:

```bash
mvn -Plinux javafx:run
```

---

## Persistence guidelines

| Do | Don't |
|----|-------|
| Versioned Jackson DTOs (`version: 1`) | Comma-separated DNA in `.vrs` |
| Validate on load; fail with user message | Silent partial load |
| Use `ObjectMapper` from Spring bean | `org.json.JSONObject` |
| Write to user data dir | Assume `resources/` is writable in JAR |

### Virology stats DTO example

```java
public record VirologyStatsDto(
    int infectedCells,
    int resistantCells,
    List<Boolean> infectionHistory
) {}
```

---

## Related documents

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [ROADMAP.md](ROADMAP.md)
