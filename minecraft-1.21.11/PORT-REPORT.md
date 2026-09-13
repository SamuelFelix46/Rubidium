> Historical prototype report. The final player release keeps only the exact math fast path. Upload budgeting described below requires a separately installed research harness AND rubidium.bench.experimentalUploads=true; it is never enabled by a property alone in normal gameplay. See the root README.md and BENCHMARKS.md for the shipped scope.

# Minecraft 1.21.11 port candidate

Prepared: 2026-09-13. This report covers only files under `minecraft-1.21.11`.

## Candidate scope

The module contains the two selected player features:

- the exact `Beardifier.getBuryContribution(double,double,double)` radius-six positive-zero fast path;
- a 2,000,000 ns / 64-job FIFO upload budget at the `LevelRenderer.compileSections(Camera)` call site.

It contains no Pulse mixin, noise-generation gate, runtime monitor, event registration, or mod initializer. The shared `common` jar is nested only to provide `BudgetedUploads`; no lifecycle path activates its other classes.

`CompatibilityPlugin` disables the Beardifier overwrite when C2ME is loaded and disables both upload mixins when Sodium is loaded. This matches the conflict boundaries established by the 1.21.1 candidate.

## Exact Minecraft API evidence

The local Mojang metadata identifies the dependency as Minecraft `1.21.11`, Java component `java-runtime-delta`, Java major 21, client SHA-1 `ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd`, and client-mappings SHA-1 `031a68bebf55d824f66d6573d8c752f0e1bf232a`.

Cached Mojang mappings resolve these exact targets:

```text
net.minecraft.world.level.levelgen.Beardifier
private static double getBuryContribution(double,double,double)

net.minecraft.client.renderer.LevelRenderer
private void compileSections(net.minecraft.client.Camera)

net.minecraft.client.renderer.chunk.SectionRenderDispatcher
private java.util.Queue toUpload
private java.util.concurrent.Executor mainThreadUploadExecutor
private java.util.Queue toClose
public void uploadAllPendingUploads()
public void dispose()
```

The Beardifier bytecode still calls `Mth.length(double,double,double)` followed by `Mth.clampedMap(distance, 0.0, 6.0, 1.0, 0.0)`. The mappings also retain `Mth.lengthSquared(double,double,double)`. This establishes the same arithmetic substitution as the tested 1.21.1 implementation.

`LevelRenderer.compileSections(Camera)` invokes `SectionRenderDispatcher.uploadAllPendingUploads()` directly. `dispose()` also invokes it, which is why the candidate redirects only the compile-loop invocation and leaves disposal unchanged.

## `toClose` ownership proof

The 1.21.11 dispatcher adds `Queue<SectionMesh> toClose`; blindly copying the 1.21.1 upload-only redirect would omit required resource cleanup. The candidate instead budgets uploads and then completely drains `toClose`, preserving vanilla's two-stage order.

Inspection covered `SectionRenderDispatcher` and every cached nest member (`RenderSection`, `CompileTask`, `RebuildTask`, and `ResortTransparencyTask`). The only writes to `toClose` are:

1. `RenderSection.setSectionMesh(SectionMesh)` atomically installs the new mesh, then enqueues the replaced mesh.
2. `RebuildTask`'s upload-future completion handler enqueues the newly built mesh if the task was cancelled or the dispatcher closed; otherwise it calls `setSectionMesh`.

Each rebuild submits one runnable to `mainThreadUploadExecutor`, which is backed by `toUpload`. That runnable uploads the complete layer map. Only its completed future invokes the handler that installs or retires the mesh. Therefore a mesh cannot enter `toClose` while its own upload runnable is still pending, and the old mesh is enqueued only after its replacement is installed. Reset-related retirement also goes through `setSectionMesh` and is already considered safe by vanilla at the same drain point.

Fully draining `toClose` after a partial `toUpload` drain therefore preserves resource lifetimes. It also preserves failure ordering: an upload exception prevents close draining, while a close exception propagates and leaves later close entries queued, matching vanilla's sequential loops. `dispose()` still calls the untouched vanilla method and fully drains both queues.

## Dependency selection

The module selects:

- `com.mojang:minecraft:1.21.11` exactly;
- official Mojang mappings;
- `net.fabricmc:fabric-loader:0.18.4`;
- `net.fabricmc.fabric-api:fabric-api:0.140.2+1.21.11`;
- Java 21 inherited from the root build;
- the root's already declared legacy `fabric-loom` 1.17.20 plugin, appropriate to the obfuscated 1.21.11 line.

This rebuild targets the requested Fabric Loader 0.18.4 for intermediary `1.21.11`. The requested Fabric API is `0.140.2+1.21.11`; its embedded `fabric.mod.json` is checked by the rebuild.

Cached artifact SHA-256 values:

```text
fabric-api-0.140.2+1.21.11.jar
Verified during the rebuild; see the Gradle dependency cache.

fabric-loader-0.18.4.jar
93044E4DD46DE5D8136701292F05E868DA096D2C9FDDB4793E4FDBCC63EFC695
```

Primary metadata:

- https://meta.fabricmc.net/v2/versions/loader/1.21.11
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml

## Tests prepared

Tests were written before production sources:

- `BeardifierMixinTest` compares raw double bits with the 1.21.11 vanilla expression inside the radius and requires positive zero at/on/outside radius six, including infinities.
- `SectionUploadDrainTest` covers a partial upload frame with a full ready-close drain, a mesh retired by a completed upload, upload-failure ordering, close-failure propagation, and preservation of later queued closes.
- The shared `BudgetedUploadsTest` already covers FIFO order, time and job limits, at-least-one progress, eventual draining, work enqueued by work, and exception propagation.

## Integration validation

Root integrated this module into the multi-version build and moved the helper to `fr.rubidium.flow.client.SectionUploadDrain`, outside the reserved mixin package. The six module tests and 42 shared tests pass; all eleven projects build successfully. The separate client harness uses the 1.21.11 `graphicsPreset()` API and Java 21.

A real Fabric 1.21.11 server loaded the remapped player JAR and passed 375,956 exact-method comparisons, then saved all dimensions and shut down. The audit report's `renderingModified=false` field describes this dedicated-server run; it does not describe the client feature set.

Final independent static review found no critical or important defect. It additionally checked a late translucent index upload to a retired mesh: closing clears the mesh's buffer map, and index upload then safely does nothing while its CPU result is closed.

Client observations and release limitations are recorded in the root BENCHMARKS.md. No complete saved-and-reloaded world equivalence claim is made for this port, and the release remains experimental.

