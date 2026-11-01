# Automated FSM Extraction from Java Programs via Bytecode Instrumentation  (Phase 2)

Srimithula · AM.SC.U4CSE23262 · Guide: Dr. Swaminathan J. · 23CSE498

A Java agent rewrites the *bytecode* of a running program so that every write to a chosen field is
reported automatically. The reported states become a trace, the Phase-1 `FSMBuilder` turns the trace
into a finite-state machine (`.dot`), and Graphviz draws it (`.png`). **No source code of the program
under analysis is modified or contains tracing code.**

## Requirements (your laptop)
* JDK 11 or newer (`java -version`, `javac -version`)
* Graphviz (`dot -V`)  — e.g. `sudo apt install graphviz`, or `brew install graphviz`
* Internet only the first time, if `lib/` is missing (ASM jars are bundled in `lib/`, so normally not needed)

## Run it
```
chmod +x build.sh fsm.sh
./build.sh                  # compiles everything and rebuilds agent/FSMAgent.jar
./fsm.sh testprogram 7      # Phase-1 demo program, now traced automatically (try -5 too)
./fsm.sh dp                 # Dining Philosophers  -> 11-state binary-abstraction FSM  (paper Fig. 8)
./fsm.sh rw                 # Readers-Writers      -> (r,w) FSM                       (paper Fig. 9)
./fsm.sh elevator           # Elevator Controller  -> up / down subgraphs             (paper Fig. 10)
./fsm.sh loops              # Algorithm 2 (loop-aware) vs Algorithm 1 on the paper's loop example (Fig 5/6)
./fsm.sh overhead           # slowdown with vs without the agent (objective O5)
./fsm.sh all                # everything
```
Outputs land in `results/<name>/` (`trace_raw.txt`, `*.dot`, `*.png`, `check.txt`). A finished set of
results is already included.

By hand, the core command is:
`java -javaagent:agent/FSMAgent.jar=<config>,<trace-out> -cp build/classes <MainClass> [args]`

## What each file is
| File | Role |
|---|---|
| `agent/src/FSMAgent.java` | `premain`: loads config, registers the class transformer; `hook()` is called by the injected bytecode |
| `agent/src/InstrumentationConfig.java` | reads `Class:field1,field2` config (which fields to watch) |
| `agent/src/FieldWriteClassVisitor.java` / `FieldWriteMethodVisitor.java` | ASM: finds `PUTFIELD`/`PUTSTATIC` of watched fields and injects a `hook(...)` call after them (stack duplicated with `DUP`/`DUP_X1`/`DUP2…` so the program's own behaviour is unchanged) |
| `agent/src/TraceGenerator.java` | keeps a running snapshot of all watched fields; writes `EVENT:k=v,...` once every field is known |
| `phase1/FSMBuilder.java` | Phase-1 builder (trace + keys -> `.dot`). Phase-2 flags, all optional and backward-compatible (Phase-1 outputs verified byte-identical on 10 cases): `--raw-ints`, `--clean`, `--subgraph attr=value` |
| `agent/src/LoopInstrumenter.java` | **Algorithm 2**: finds loops in bytecode (backward jumps), inserts `loopHeader/loopExit` calls; the agent keeps one loop stack per thread and stamps `loop=Ln` on every trace line (agent arg 3 = `loops`) |
| `tools/LoopAwareFSMBuilder.java` | **Algorithm 2** builder: state = hidden loop id + key values; edges labelled with counts |
| `tools/ModelAbstractor.java` | **Algorithm 3**: binary (`key:bin:=:EATING:E:~E`) and multivalued (`f:multi:3:6` -> `<3`, `3-6`, `>6`) abstraction |
| `tools/PredicateAbstractor.java` | paper's **binary** abstraction (`state_i = EATING` -> `E` / `~E`) |
| `tools/StateAbstractor.java` | own **count** abstraction (5 thread states -> r = #READING, w = #WRITING) |
| `verification/TraceChecker.java` | checks the paper's safety properties (dp, rw, elevator) on the raw trace |
| `benchmarks/*.java` | programs analysed: `TestProgram`, `DiningPhilosophers`, `ReadersWriters`, `ElevatorController`, `LoopDemo` (paper's loop example), `OverheadBenchmark`, `NestedWriteTest` (sanity test) |
| `configs/*.instr`, `*.keys` | which fields the agent watches / which variables are key variables for the FSM |

## Honest limits (know these for the viva)
* Only **field writes** (`PUTFIELD`/`PUTSTATIC`) are seen. Local variables and array elements are not — so the
  benchmarks keep their observable state in named fields (this is why `TestProgram`'s x,y became static fields).
* The model is the set of behaviours **seen in the runs**, not all possible behaviours. More runs = more coverage.
* Models are meaningful when state updates are properly synchronized. `DiningPhilosophers` and `ReadersWriters`
  originally reset the state *after* releasing the lock (a real race: adjacent philosophers shown eating in 14/40
  runs, reader+writer together in 5/40 runs). Fixed by resetting before release: 0/40 violations each, and DP gives
  exactly the paper's 11 states.
* The BCI technique itself is described in the base paper (Sec. 3.2); this project is a standalone implementation of it.
* All three algorithms are implemented:
  - Algorithm 1 (basic extraction): `FSMBuilder` (Phase 1) fed by the agent's automatic trace.
  - Algorithm 2 (loop-aware): loops are detected from bytecode back-edges instead of LoopStart/LoopEnd events from a
    tracer (the paper's footnote allows this). Limits: loops in classes not listed in the config (nested classes of a
    tracked class are included) are not seen; an exception that leaves a loop can leave a stale entry on the loop stack
    (cosmetic); `switch`-based back-edges are not treated as loops.
  - Algorithm 3 (abstraction): unary (`FSMBuilder --subgraph`), binary (`PredicateAbstractor` / `ModelAbstractor`),
    multivalued (`ModelAbstractor`), plus an own count-based variant (`StateAbstractor`). Unary is applied by the
    builder after the other functions; the order does not matter because each function acts on its own attribute.
  - Not done: the paper's temporal-logic path-property checking beyond the three hand-coded properties in `TraceChecker`.
* Overhead is ~0 for realistic programs (sleep/lock dominated) but huge for a tight loop of field writes (see results/overhead).

## Tests and Maven
```
./test.sh        # builds, then runs the JUnit 5 suite (31 tests) - no Maven needed
mvn test         # same tests through Maven (needs the agent built once with ./build.sh)
mvn package      # builds target/FSMAgent.jar
```
The suite checks: Phase-1 output unchanged (10 golden files), the abstraction functions, that the property checker
accepts good traces *and rejects bad ones*, the agent on every benchmark, Algorithm 2 (same value inside/outside a
loop is distinguished), and that the agent does not change a program's output.
`pom.xml` could not be run in the environment where this was written (no Maven Central access there); `build.sh` and
`test.sh` are the tested path. If `mvn` complains, tell your guide the shell scripts are the supported build.

## Picture style
`FSMBuilder --pretty --title "..."` draws presentation-quality diagrams (small START, short edge labels, parallel arrows
merged, multi-line nodes; for Dining Philosophers each state is a row of 5 cells with the eating philosophers highlighted,
and states are laid out in rows by number of eaters, as in the paper's Figure 8). `./fsm.sh` uses it for every picture.
Without `--pretty` the original Phase-1 drawing is produced unchanged.
