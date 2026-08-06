# Milestone 10: Production Synthesis Coordinator Benchmark Checklist

## Focus Area: Bounded Queue, Discarding Stale Results, Eviction

## Execution Steps
1. [ ] Begin continuous playback of a book.
2. [ ] While playing, verify memory usage remains flat (bounded queue works, not buffering entire chapters).
3. [ ] Perform a "Rapid Seek" (jump ahead 10 paragraphs instantly).
4. [ ] Verify that audio from the *old* generation does not play (no stale result leakage).
5. [ ] Verify that the playback starts from the new seek location rapidly.
6. [ ] Replay previously synthesized chunks within a single session and ensure no ONNX generation delay (verify disk cache hit).

## Performance/Memory Criteria
- [ ] Active memory peak < 250MB during continuous playback.
- [ ] Storage cache max boundary is respected (max size 50MB, old WAVs correctly evicted).
- [ ] No coroutine leaks after 5 minutes of continuous playback (number of active jobs should remain constant).
