# G1 — Multiplatform viability spike, P0 result

*2026-09-19*

**Status: PARTIAL PASS.** The specific numeric risk the architecture names is
measured and quantified. The Kotlin/JS toolchain itself remains unverified and
moves to your machine.

---

## What the architecture asked for

FINAL_ARCHITECTURE §3 and §13 define G1 as: port the prototype's real angle and
FSM code, compile to JS, call it from React with a hard-coded landmark sequence,
assert the rep count matches the JVM, and inspect the generated `.d.ts` and build
time.

Two parts of that are no longer in scope or were not possible:

- **React is cut** (see `DECISIONS.md` P0-2), so no React integration was
  attempted. G1 reduces to: same core, both targets, same discrete outputs.
- **The prototype was not available.** Access to `E:\kinesis_old` was not
  granted during P0, so a structurally representative implementation was used
  instead of the real one — bilateral signal shape, a three-point joint angle
  through `atan2`/`acos`/`sqrt`/`sin`/`cos`, and a hysteresis rep FSM.

## What blocked the full check

The sandbox this ran in cannot reach Maven:

```
repo1.maven.org:443     403 to CONNECT (egress policy denial)
plugins.gradle.org:443  403 to CONNECT (egress policy denial)
```

That is an organisation egress policy, not a misconfiguration, and it was not
worked around. Gradle 8.14.3 ships `kotlin-compiler-embeddable-2.0.21.jar`, but
the Kotlin/JS standard-library `.klib` is not bundled and only comes from Maven
Central, so Kotlin/JS compilation was impossible offline.

## What was measured instead

FINAL_ARCHITECTURE §3 states the risk precisely: *"`kotlin.math` on JS maps to
the JavaScript engine's `Math`, which is not guaranteed bit-identical to the
JVM's."*

That risk is testable without the Kotlin toolchain, because on the JVM
`kotlin.math.atan2` **is** `java.lang.Math.atan2`, and on Kotlin/JS it **is**
`Math.atan2`. So the comparison was run directly between the two underlying
implementations, on identical deterministic inputs, at bit level.

Environment: JDK 21.0.10 (OpenJDK, Ubuntu) · Node 22.22.2 · V8 12.4.254.21 ·
x86-64 Linux.

### Test 1 — the squat track (200 frames, 5 reps)

Identical on both platforms, bit for bit:

| Output | JVM | JS |
|---|---|---|
| Rep count | 5 | 5 |
| Phase sequence | `DBATDBATDBATDBATDBATDB` | `DBATDBATDBATDBATDBATDB` |
| atan2 angle sum (raw bits) | `40d7700000000000` | `40d7700000000000` |
| acos angle sum (raw bits) | `40d7700000000000` | `40d7700000000000` |

This track alone would have suggested bit-identical output. It is not
representative — the angles it visits are unusually well-conditioned. A wider
sweep was needed, and this is worth remembering as a lesson about golden files
chosen from a single tidy trajectory.

### Test 2 — one million random samples, 64-bit XOR checksum

| Function | Result |
|---|---|
| `sqrt` | **Bit-identical.** IEEE-754 mandates correct rounding for square root. |
| `sin`, `cos`, `atan2`, `acos`, composed angle | **Checksums differ.** |

### Test 3 — magnitude of the divergence (200,000 samples)

| Function | Samples differing | % | Max relative error |
|---|---|---|---|
| `sin` | 6,697 | 3.35% | 2.219 × 10⁻¹⁶ |
| `cos` | 6,592 | 3.30% | 2.220 × 10⁻¹⁶ |
| `atan2` | 3,598 | 1.80% | 4.225 × 10⁻¹⁶ |
| `acos` | 4,479 | 2.24% | 2.842 × 10⁻¹⁴ |
| Composed joint angle (degrees) | 3,012 | 1.51% | 5.153 × 10⁻¹⁶ |

**Maximum absolute difference in a computed joint angle: 4.263 × 10⁻¹⁴ degrees**,
over an angle range of 0.0008° to 179.999°.

## What this means

**FINAL_ARCHITECTURE was right to retract "bit-identical."** The functions do
diverge, on 1.5–3.4% of inputs. Anyone asserting exact equality on continuous
metrics would have hit failing tests.

**The stated tolerance is correct and conservative.** The architecture claims
continuous metrics agree within 1 × 10⁻⁹ relative. Measured worst case is
2.8 × 10⁻¹⁴ — roughly five orders of magnitude inside the claim. Keep the claim
as written; do not tighten it on the strength of this, and do not loosen it.

**Discrete outputs are safe by an enormous margin.** Rep counting depends on a
hysteresis band measured in degrees — the spike used 100° and 150°. The
divergence is 4 × 10⁻¹⁴ degrees. That is about thirteen orders of magnitude of
headroom. A floating-point difference of this size cannot flip a phase
transition or a rep count. The architecture's claim of *identical discrete
outputs* holds.

## What remains unverified

These move to your machine and should be closed before P11, though none of them
block P1–P10:

1. **Does the Kotlin/JS compiler build the core at all?** Untested. This is the
   actual remaining G1 question.
2. **The `@JsExport` facade and its generated `.d.ts`.** Untested.
3. **Gradle KMP build time.** Untested.
4. **Other JavaScript engines.** ECMAScript does not require correctly-rounded
   transcendental functions, so V8's results do not generalise. If the PWA is
   ever built, Firefox uses SpiderMonkey and Safari uses JavaScriptCore, either
   of which may diverge more than V8 does. The tolerance-based claim covers
   this; an exactness claim would not.
5. **CPU architecture.** Tested on x86-64 only. A different `libm` on ARM could
   differ.

## How to finish G1 on your machine

The runnable spike is committed at `tools/g1-spike/`. It has `jvm()` and `js()`
targets and a golden test that runs on both.

```
cd tools/g1-spike
gradle jvmTest jsNodeTest
```

Both targets must pass. Then inspect the generated declarations:

```
gradle jsProductionLibraryCompileSync
cat build/compileSync/js/main/productionLibrary/kotlin/kinesis-g1-spike.d.ts
```

Paste the output and the build timing. If the JS target fails to compile, the
documented fallback is the replay-only web client, not a TypeScript rewrite.
