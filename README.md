# DNA Replicator / Virus Builder Simulator v6

Educational desktop game: splice DNA, fold proteins, build a virus, and simulate infection on a cell graph. Built with **Java 21**, **Spring Boot** (dependency injection), and **JavaFX**.

## Prerequisites

- JDK 21+
- Maven 3.9+
- JavaFX native libraries (pulled via Maven classifiers)

## Quick start

```bash
# Compile and test
mvn verify

# Run the desktop app (Windows profile active by default on Windows)
mvn javafx:run
```

### Cross-platform JavaFX

Maven selects the JavaFX classifier automatically on **Windows** and **macOS**. On Linux, activate the profile explicitly:

```bash
mvn -Plinux javafx:run
mvn -Pmac javafx:run      # macOS (also auto-activated on Mac)
```

## Project layout

| Path | Description |
|------|-------------|
| `src/main/java/com/xai/dnareplicator/` | Application source |
| `src/main/resources/application.properties` | Simulation tuning |
| `resources/` | Runtime saves (FASTA, virus state, stats) — created on first save |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design |
| [ROADMAP.md](ROADMAP.md) | Delivery phases |
| [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) | Contributor guide |

## Player workflow

1. **Splice DNA** — Select two fragments, move them close, click *Splice DNA* (LCS alignment + mutation risk).
2. **Fold Proteins** — Click *Fold Proteins* after splicing creates enzymes.
3. **Build Virus** — Fold enough proteins, then *Build Virus*.
4. **Simulate Infection** — *Simulate Infection* runs pathfinding and BFS spread; level and stats update.

## Configuration

Edit [`src/main/resources/application.properties`](src/main/resources/application.properties). Values bind to `SimulationProperties` at startup (see `com.xai.dnareplicator.config`).

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — Layers, diagrams, refactoring plan
- [ROADMAP.md](ROADMAP.md) — Stabilization → Refactoring → Features → Polish
- [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) — How to add mechanics and tests

## License

Educational project — see repository owner for terms.
