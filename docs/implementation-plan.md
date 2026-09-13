# Rubidium 1.21.1 Implementation Plan

**Goal:** Build and measure an automatic Fabric 1.21.1 experimental mod before deciding whether it qualifies as a stable release or can be ported.
**Architecture:** Pure Java controller and bounded asynchronous admission of vanilla noise tasks. Preserve the original supplier, executor and result, without blocking Minecraft threads. Separate benchmark mod drives controlled server workloads.
**Tech Stack:** Java 21, Fabric Loom, Mojang mappings, Fabric API, JUnit 5, Gradle.
**Spec:** ../../../../outputs/Rubidium-conception.md (approved by user).

## Global constraints
- Automatic operation: no player menu or required configuration.
- Same vanilla generation supplier and executor; no new parallel world writes.
- No fabricated performance or compatibility claims; untested versions never packaged as supported.
- Work isolated in work/rubidium; player deliverables in outputs.
- Finite Rubidium-owned queues. Overflow returns to vanilla scheduling and records bypasses, not a hard global memory guarantee.
- Test-only benchmark activation via separate dev mod; no benchmarks in normal gameplay.

## Tasks

### 1. Pure controller and telemetry
- [ ] Implement from failing behavioral tests as specified in ../core-brief.md.
- [ ] Verify controller respects bounds, fast overload reduction and delayed recovery.
- [ ] Verify frame statistics on literal samples and concurrent recorder use.
- [ ] Review implementation against brief; record in progress.md.

### 2. Admission gate and Minecraft integration
Files: common/src/main/java/fr/rubidium/flow/core/AsyncGate.java; minecraft-1.21.1/src/main/java/fr/rubidium/flow/{Rubidium,RuntimeMonitor}.java; mixin/NoiseGeneratorMixin.java; mixin/client/MinecraftMixin.java; resources/fabric.mod.json and mixin descriptors.
- [ ] Test `AsyncGate.submit(Supplier<T>, Executor)` with held tasks: second task must not execute until first exits at limit 1. Expected results are literal 11 and 22.
- [ ] Test failing supplier, rejecting executor, saturation fallback, changing limit and draining shutdown; every accepted future must complete exactly once.
- [ ] Implement bounded queue with completion-triggered dispatch and no sleeps or synchronous waiting. Gate applies only to standalone noise supplier after bytecode audit confirms no nested gated dependencies.
- [ ] Connect controller limit to gate. Sampling occurs on a daemon scheduled executor at 250 ms, with lifecycle start/stop, one instance per server process. Render frame timestamps stored without per-frame allocations; stale frames ignored.
- [ ] Preserve vanilla supplier and executor through a narrowly scoped redirect of fillFromNoise supplyAsync. Detect C2ME before applying the mixin; preserve independent telemetry.
- [ ] Compile and start a dedicated server to exercise transformed classes; client entry points isolated.

### 3. Reproducible Minecraft harness
Files: benchmark/src/main/java/fr/rubidium/flow/benchmark/BenchmarkMod.java; scripts/run-benchmarks.ps1; scripts/compare-worlds.py.
- [ ] Create separate dev mod to generate a finite, specified coordinate grid, with fixed concurrency and seed. Instrumentation-only mode disables gate via runtime API before generation.
- [ ] Count completions, failed futures, per-chunk canonical hashes (block registry IDs + state properties, biomes, heightmaps, structures), wall time, process CPU, heap and GC; export JSON.
- [ ] Run identical warmed-up A/B cases on new worlds and export raw evidence. Mark instrumentation baseline as Fabric + API + harness, not strict vanilla.
- [ ] Compare hashes and summarize measurements; do not claim client FPS without a rendered client benchmark.
- [ ] Investigate regressions. Active module remains experimental if acceptance criteria cannot be established. Ports wait for stable 1.21.1.

### 4. Review and delivery
- [ ] Review whole change, fix important issues, rerun affected tests/builds.
- [ ] Write README, CHANGELOG, COMPATIBILITY, BENCHMARKS, CONTRIBUTING and French descriptions with accurate feature status.
- [ ] Package verified JAR, sources, reports, wrapper and SHA-256 sums in clearly labelled experimental ZIP if broad release criteria remain unmet.
- [ ] Inspect ZIP entries and verify packaged JAR hashes match tested build.
