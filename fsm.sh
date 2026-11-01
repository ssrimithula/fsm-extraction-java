#!/usr/bin/env bash
# One-command driver for the whole pipeline.  Usage:  ./fsm.sh <command> [args]
#   build                      compile the agent jar, tools and benchmarks
#   testprogram [number]       Phase-1 demo program, now traced automatically (default input 5)
#   dp [meals] [runs]          Dining Philosophers  -> binary abstraction (paper Fig. 8)
#   rw [iterations] [runs]     Readers-Writers      -> (r,w) abstraction   (paper Fig. 9)
#   elevator [trips]           Elevator Controller  -> up/down subgraphs   (paper Fig. 10)
#   loops                      Algorithm 2 (loop-aware) vs Algorithm 1 on the paper's loop examples (Fig 5/6)
#   overhead [runs]            slowdown with vs without the agent (objective O5)
#   all                        everything above
# Every run writes its files into results/<name>/ (trace, .dot, .png, check report).
set -e
cd "$(dirname "$0")"
R=$PWD; JAR=$R/agent/FSMAgent.jar; CLS=$R/build/classes; CFG=$R/configs
J="java -cp $CLS"
quiet(){ grep -v "Picked up JAVA_TOOL_OPTIONS" || true; }
need_build(){ [ -f "$JAR" ] && [ -d "$CLS" ] || ./build.sh; }
render(){ # <dir> <name>   (FSMBuilder always writes fsm.dot in the current directory)
  mv fsm.dot "$1.dot"; dot -Tpng -Gdpi=130 "$1.dot" -o "$1.png"; echo "    -> $(basename $PWD)/$1.png"; }

cmd=${1:-help}; shift || true
case $cmd in
build) ./build.sh 2>&1 | quiet ;;

testprogram)
  need_build; n=${1:-5}; O=results/testprogram; mkdir -p $O; cd $O
  echo "[1] run TestProgram with the agent attached (input=$n) - the program source has no tracing code"
  echo $n | java -javaagent:$JAR=$CFG/testprogram.instr,trace.txt -cp $CLS TestProgram 2>&1 | quiet >/dev/null
  echo "[2] FSMBuilder (Phase 1, unchanged logic): trace + keys -> .dot"
  $J FSMBuilder trace.txt $CFG/testprogram.keys --pretty --title "TestProgram: states of x and y" 2>&1 | quiet >/dev/null
  echo "[3] Graphviz: .dot -> .png"; render fsm_testprogram ;;

dp)
  need_build; meals=${1:-3}; runs=${2:-10}; O=results/dining_philosophers; mkdir -p $O; cd $O; rm -f trace_run*.txt
  echo "[1] run DiningPhilosophers $runs times (meals=$meals) with the agent attached"
  for i in $(seq 1 $runs); do
    java -javaagent:$JAR=$CFG/dp.instr,trace_run$i.txt -cp $CLS DiningPhilosophers $meals 2>&1 | quiet | grep -i "violation" || true; done
  cat trace_run*.txt > trace_raw.txt
  echo "[2] check the safety property on the raw trace (no two adjacent philosophers eating)"
  $J TraceChecker dp trace_raw.txt 2>&1 | quiet | tee check.txt
  echo "[3] binary predicate abstraction (paper Alg. 3):  state_i -> E / ~E"
  $J PredicateAbstractor trace_raw.txt trace_binary.txt state0,state1,state2,state3,state4 EATING E '~E' 2>&1 | quiet
  echo "    distinct abstract states: $(cut -d: -f2 trace_binary.txt | sort -u | wc -l)  (paper's maximum: 11)"
  echo "[4] FSMBuilder + Graphviz"
  $J FSMBuilder trace_binary.txt $CFG/dp.keys --clean --pretty --title "Dining Philosophers: binary abstraction (E = eating)" 2>&1 | quiet >/dev/null; render fsm_dp_binary ;;

rw)
  need_build; it=${1:-3}; runs=${2:-10}; O=results/readers_writers; mkdir -p $O; cd $O; rm -f trace_run*.txt
  echo "[1] run ReadersWriters $runs times (iterations=$it) with the agent attached"
  for i in $(seq 1 $runs); do
    java -javaagent:$JAR=$CFG/rw.instr,trace_run$i.txt -cp $CLS ReadersWriters $it 2>&1 | quiet | grep -i "violation" || true; done
  cat trace_run*.txt > trace_raw.txt
  echo "[2] check G[(r>0->w=0) && (w>0->r=0) && (w<=1)] on the raw trace"
  $J TraceChecker rw trace_raw.txt 2>&1 | quiet | tee check.txt
  echo "[3] count abstraction: 5 thread states -> (r = #READING, w = #WRITING)"
  $J StateAbstractor trace_raw.txt trace_rw.txt reader0State,reader1State,reader2State,writer0State,writer1State READING,WRITING 2>&1 | quiet
  echo "[4] FSMBuilder + Graphviz"
  $J FSMBuilder trace_rw.txt $CFG/rw.keys --clean --pretty --title "Readers-Writers: reading = number of readers, writing = number of writers" 2>&1 | quiet >/dev/null; render fsm_rw ;;

elevator)
  need_build; trips=${1:-40}; O=results/elevator; mkdir -p $O; cd $O
  echo "[1] run ElevatorController (9 floors, 4 passengers, $trips trips each) with the agent attached"
  java -javaagent:$JAR=$CFG/elevator.instr,trace_raw.txt -cp $CLS ElevatorController $trips 4 2>&1 | quiet | grep -v "^\[FSMAgent\]"
  echo "[2] check the paper's elevator property on the raw trace"
  $J TraceChecker elevator trace_raw.txt 2>&1 | quiet | tee check.txt
  echo "[3] FSMBuilder: full model, then unary/subgraph abstraction on d=up and d=down (paper Fig. 10)"
  $J FSMBuilder trace_raw.txt $CFG/elevator.keys --raw-ints --clean --pretty --title "Elevator: full model (f = floor, d = direction)" 2>&1 | quiet >/dev/null; render fsm_elevator_full
  $J FSMBuilder trace_raw.txt $CFG/elevator.keys --raw-ints --clean --pretty --title "Elevator: unary abstraction, d = up (paper Fig. 10)" --subgraph d=up 2>&1 | quiet >/dev/null; render fsm_elevator_up
  $J FSMBuilder trace_raw.txt $CFG/elevator.keys --raw-ints --clean --pretty --title "Elevator: unary abstraction, d = down (paper Fig. 10)" --subgraph d=down 2>&1 | quiet >/dev/null; render fsm_elevator_down
  echo "[4] Algorithm 3, multivalued abstraction of the floor: f -> <3 | 3-6 | >6, combined with unary d=up"
  $J ModelAbstractor trace_raw.txt trace_multi.txt f:multi:3:6 2>&1 | quiet
  $J FSMBuilder trace_multi.txt $CFG/elevator.keys --clean --pretty --title "Elevator going up: floor abstracted to <3 / 3-6 / >6 (multivalued)" --subgraph d=up 2>&1 | quiet >/dev/null; render fsm_elevator_up_multivalued ;;

loops)
  need_build; O=results/loops; mkdir -p $O; cd $O
  echo "[1] run LoopDemo (the loop example of paper Fig 5/6) with the agent in loop-aware mode (3rd agent argument: loops)"
  java -javaagent:$JAR=$CFG/loopdemo.instr,trace_loops.txt,loops -cp $CLS LoopDemo 2>&1 | quiet | grep -v "^\[FSMAgent\]"
  echo "    loops found in the bytecode (<trace>.loops):"; sed 's/^/      /' trace_loops.txt.loops
  echo "[2] Algorithm 1 (loop-unaware FSMBuilder): equal values merge -> invents a cycle that is not in the source"
  $J FSMBuilder trace_loops.txt $CFG/loopdemo.keys --raw-ints --clean --pretty --title "Algorithm 1 (loop-unaware): invents loop edges" 2>&1 | quiet >/dev/null; render fsm_alg1
  echo "[3] Algorithm 2 (LoopAwareFSMBuilder): loop id is a hidden part of the state; edges carry counts"
  $J LoopAwareFSMBuilder trace_loops.txt $CFG/loopdemo.keys fsm_alg2.dot --raw-ints --title "Algorithm 2 (loop-aware): the paper's loop example, no invented cycles" 2>&1 | quiet
  dot -Tpng -Gdpi=130 fsm_alg2.dot -o fsm_alg2.png; echo "    -> loops/fsm_alg2.png" ;;

overhead)
  need_build; runs=${1:-10}; O=results/overhead; mkdir -p $O; cd $O
  stat(){ awk '{s+=$1; q+=$1*$1; n++} END{m=s/n; sd=sqrt((q/n-m*m)>0?(q/n-m*m):0); printf "%.0f ms (sd %.0f)", m, sd}'; }
  one(){ # name cfg class args...  -> prints mean for baseline and agent
    name=$1; cfg=$2; cls=$3; shift 3
    : > base.txt; : > agent.txt
    for i in $(seq 1 $runs); do   # alternate to spread machine noise evenly
      java -cp $CLS $cls "$@" 2>&1 | quiet | sed -n 's/.*elapsed_ms=\([0-9]*\).*/\1/p' >> base.txt
      java -javaagent:$JAR=$CFG/$cfg,/dev/null -cp $CLS $cls "$@" 2>&1 | quiet | sed -n 's/.*elapsed_ms=\([0-9]*\).*/\1/p' >> agent.txt
    done
    b=$(awk '{s+=$1;n++} END{print s/n}' base.txt); a=$(awk '{s+=$1;n++} END{print s/n}' agent.txt)
    printf "%-22s without: %-18s with agent: %-18s slowdown: %.2fx\n" "$name" "$(stat < base.txt)" "$(stat < agent.txt)" "$(awk -v a=$a -v b=$b 'BEGIN{print a/b}')"; }
  echo "overhead over $runs alternating runs per program (mean, sd of elapsed time printed by each program)" | tee overhead.txt
  { one "tight loop (2M writes)" bench.instr OverheadBenchmark 1000000
    one "DiningPhilosophers"     dp.instr DiningPhilosophers 30
    one "ReadersWriters"         rw.instr ReadersWriters 30
    one "ElevatorController"     elevator.instr ElevatorController 40 4; } | tee -a overhead.txt ;;

all) for c in testprogram dp rw elevator loops overhead; do echo; echo "=== $c ==="; ./fsm.sh $c; done ;;

*) sed -n 2,12p "$0" ;;
esac
