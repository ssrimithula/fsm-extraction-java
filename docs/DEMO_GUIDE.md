# Demo guide (about 10 minutes)

Open a terminal in the `FSMProject` folder. Run `./build.sh` and `./fsm.sh all` the evening before so every picture
already exists in `results/` (open them with `xdg-open results/<folder>/<file>.png`). If a live run misbehaves, open the
saved picture and say: "this is the output of the same command".

## Order, commands and what to say

| # | Show | Command | One-sentence caption (say this) |
|---|------|---------|----------------------------------|
| 0 | Open `benchmarks/DiningPhilosophers.java` | none | "This is the program under analysis. There is no tracing code in it; in Phase 1 I had to add println lines by hand." |
| 1 | TestProgram | `./fsm.sh testprogram 7` then `xdg-open results/testprogram/fsm_testprogram.png` | "The agent rewrote the program's bytecode as it loaded, recorded every write to x and y, and FSMBuilder drew this. I never edited the program." |
| 2 | Dining Philosophers | `./fsm.sh dp` then `xdg-open results/dining_philosophers/fsm_dp_binary.png` | "Each box is one moment: five cells, one per philosopher, orange = eating. 11 boxes in rows of 0, 1 and 2 eaters: exactly the paper's Figure 8. The checker confirms no two neighbours ever eat together." |
| 3 | The race story | (talk) | "My first version showed neighbours eating together. I checked, and it was a real bug in my own program: it reset a philosopher's state after releasing the forks. Fixed: violations went from 14 of 40 runs to 0 of 40." |
| 4 | Readers-Writers | `./fsm.sh rw` then `xdg-open results/readers_writers/fsm_rw.png` | "State = (number of readers, number of writers). Readers can overlap (0 to 3), a writer is always alone. That is the paper's Figure 9 shape." |
| 5 | Elevator (use case) | `./fsm.sh elevator` then open `fsm_elevator_up.png`, `fsm_elevator_down.png` | "9 floors, 4 passengers as threads. Unary abstraction splits the model by direction: going up visits floors 1 to 9, going down 9 to 1, as in the paper's Figure 10. The paper's rule (direction changes only on the same floor) holds." |
| 6 | Multivalued abstraction | open `results/elevator/fsm_elevator_up_multivalued.png` | "Algorithm 3 can also group floors into ranges: low (<3), middle (3-6), high (>6)." |
| 7 | Algorithm 2 | `./fsm.sh loops` then open `fsm_alg1.png` and `fsm_alg2.png` | "In Algorithm 1 the value x=2 appears before and inside the loop and gets merged, which invents an arrow x=3 to x=2 that is not in the code. Algorithm 2 keeps a hidden loop id: the white x=2 is outside the loop, the blue x=2 is inside it. Colours show which loop a state belongs to." |
| 8 | Proof it is solid | `./test.sh` | "31 automated tests, including that the property checker really rejects bad traces." |
| 9 | Overhead | `cat results/overhead/overhead.txt` | "About 1x for realistic programs; about 175x for a tight loop of field writes, which is the worst case." |

## Do not do live
- `./fsm.sh overhead` (takes minutes); show the saved `overhead.txt` instead.
- `./build.sh` (do it beforehand).

## If asked "what are the limits?"
Only field writes are seen (not local variables or array elements); the model covers only the behaviour seen in the runs;
the technique is the paper's, and this is a standalone implementation of it.
