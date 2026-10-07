# Solver parallelism measurements

Measured on the connected Pixel 9 Pro on October 6, 2026, using the debug app and the default 40-move, 192-state bounds.

Seven configurations cover three boards: the enhanced Fire/Dark screenshot, the mixed-color Wood TPA screenshot, and a deterministic shuffled Fire/Water board. Each configuration warms up both modes, then measures five serial/parallel pairs with alternating execution order. Serial uses one dispatcher worker; parallel uses the default CPU dispatcher. Every warm-up and measured result must equal the serial baseline, including its route, analysis, and requirement status. Times exclude instrumentation startup.

Two implementations were compared, totaling 140 measured solves plus warm-ups. Launching concurrent work at every search depth increased median time by 9–42% in the six configurations with two passes. Running each complete search pass concurrently removed those repeated launches and produced the following medians:

| Configuration | One worker | Parallel workers | Median reduction |
| --- | ---: | ---: | ---: |
| Dark TPA | 1,274 ms | 808 ms | 37% |
| Dark TPA, minimum 5 | 1,311 ms | 821 ms | 37% |
| Wood TPA, minimum 5 | 1,897 ms | 1,057 ms | 44% |
| Fire L shape, minimum 8 | 1,287 ms | 634 ms | 51% |
| Fire VDP | 1,384 ms | 767 ms | 45% |
| Water damage | 1,378 ms | 989 ms | 28% |
| Combos only control | 686 ms | 657 ms | 4% |

The combos-only control has one search pass, so its small difference is measurement variation rather than a parallel-search benefit. The retained implementation runs complete exploration passes concurrently, shares a synchronized bounded cache, computes board analysis outside the lock, and merges final results in fixed strategy order. A targeted fallback starts afterward only when the shared candidates miss the requested minimum.

These are observed timings for these fixtures on this phone, not guaranteed speedups or timing assertions. The recorded whole-pass samples ranged from 588–1,174 ms in parallel for the two-pass cases. Cancellation and deterministic concurrent results are covered separately by unit tests. See `BoardSolverPerformanceTest` for the repeatable measurement procedure.
