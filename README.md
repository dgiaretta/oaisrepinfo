# oais-structure-adapters

Bridges the `StructureRepInfo` / `RepresentationInformation` interfaces from
[`dgiaretta/OAISIFBBCandidiate`'s `oaisCore`](https://github.com/dgiaretta/OAISIFBBCandidiate/tree/master/oaisCore)
onto three concrete binary-parsing engines - **DRB**, **Kaitai Struct** and
**Apache Daffodil (DFDL)** - so that any of them can stand behind a
`StructureRepInfo` in an OAIS Information Package without oaisCore, or code
built on it, needing to know which one.

## The idea

`StructureRepInfo` in oaisCore is currently an empty marker interface (a
subtype of `RepresentationInformation`, which is itself descriptive: a
category, and Structure/Semantic/Other sub-components). This project adds
one new, small interface that any `StructureRepInfo` can additionally
implement:

```java
public interface ExecutableStructureRepInfo
        extends StructureRepInfo, ExecutableRepresentationInformation<DigitalObject, StructureNode> {
    StructureNode apply(DigitalObject digitalObject);
    FormatSpecification getFormatSpecification();
}
```

A DRB format descriptor, a Kaitai-Struct-generated parser class, and a
compiled DFDL schema are all, in exactly the OAIS sense, pieces of
Representation Information that happen to be *executable*: each is a formal
description of a bitstream format that a matching engine can run against an
actual bitstream to recover its structure. `ExecutableStructureRepInfo` is
the seam that lets that capability sit on oaisCore's model without oaisCore
depending on any of the three engines - or on any engine at all, since
`oais-structure-api` (the only module a consumer needs to compile against)
has no dependency beyond oaisCore and the JDK.

```
oais-structure-api      <- StructureNode, ExecutableStructureRepInfo, the
                            StructureInterpreterProvider SPI. Depends only
                            on oaisCore.
oais-structure-dfdl      <- ExecutableStructureRepInfo backed by Apache Daffodil.
oais-structure-kaitai    <- ExecutableStructureRepInfo backed by a Kaitai-Struct-
                            generated Java class, bridged *generically* via
                            reflection over its getters (one implementation
                            works for every .ksy-derived class).
oais-structure-drb       <- ExecutableStructureRepInfo backed by DRB, bridged
                            *entirely* by reflection (see below for why).
oais-structure-demo      <- Runnable example wiring all of the above into an
                            actual oaisCore InformationObject.
```

A fourth engine can be added the same way `oais-structure-dfdl` etc. were,
as a self-contained module implementing `StructureInterpreterProvider` and
registering it under
`META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider`
- `oais-structure-api` and the other adapters never need to change.

## Design choices worth knowing about

- **`StructureNode`** is the one shape every engine's result is reduced to:
  a name, a kind (`COMPOSITE` / `ARRAY` / `LEAF`), a value for leaves,
  ordered children, optional attributes, and an optional byte/bit range.
  `DefaultStructureNode` is a plain, immutable, hand-buildable
  implementation (useful for tests and fixtures); the three adapters each
  wrap their engine's native result directly instead, so a large parsed
  structure is not copied.
- **`AbstractExecutableStructureRepInfo`** extends oaisCore's own
  `StructureRepInfoRefImpl` (not a fresh implementation of
  `StructureRepInfo`), so every executable Structure RepInfo built on it
  remains a fully-fledged oaisCore `RepresentationInformation` - it can
  carry a category, sub-components, and serialise through the existing
  Jackson wiring - as well as being directly callable.
- **`FormatSpecification`** is deliberately thin and engine-specific
  subclasses carry the real configuration, because what a "specification"
  consists of genuinely differs: DFDL and DRB consume a schema resource at
  run time (a URI/bytes); Kaitai Struct's schema is compiled *ahead of
  time*, by the separate `ksc` compiler, into a Java class, and is not
  bytes at all by the time this project sees it.
- **`StructureInterpreterFactory`** realises oaisCore's existing
  `AbstractOaisFactory<T>` pattern via `ServiceLoader` discovery of
  `StructureInterpreterProvider`s.

## What was, and was not, verified

This project was authored in a sandboxed environment whose outbound network
access does not reach Maven Central (`repo.maven.apache.org` returned
`403 Forbidden` through the environment's egress proxy - not something to
route around, so it was not). That materially shaped what could be checked
before handing this over:

- **`oais-structure-api`** was compiled for real against the actual
  `oaisCore` sources (cloned from the GitHub repo) and the real
  `jackson-databind`/`jackson-annotations`/`jackson-core` 2.16.1 jars
  (found already present in this environment), and exercised with a
  hand-run smoke test (build a tree, print it, round-trip it through
  `DefaultStructureNode.copyOf`, wire up `StructureInterpreterFactory` with
  a fake provider, check both its error paths). All of that passed.
- **`oais-structure-dfdl`, `oais-structure-kaitai` and `oais-structure-drb`**
  could not be compiled against their real engines (`daffodil-japi`,
  `kaitai-struct-runtime`, and DRB - the last of which is not on Maven
  Central at all under any groupId this project could confirm). Instead,
  each adapter's actual logic was compiled and run against a small,
  hand-written stand-in for that engine's public API - matching the
  method/class shapes documented for Kaitai's Java target and Daffodil's
  JAPI as closely as this project's authors could determine - and produced
  correct `StructureNode` trees from real bytes in every case (including
  through a full `InformationObjectRefImpl` in `oais-structure-demo`, with
  `StructureInterpreterFactory` discovering both providers via
  `ServiceLoader`). **This proves the bridging logic is sound; it does not
  prove the exact method names guessed for the real libraries are
  correct.** Concretely:
  - `oais-structure-dfdl`'s use of `org.apache.daffodil.japi.*`
    (`Daffodil.compiler()`, `Compiler#compileSource`,
    `ProcessorFactory#onPath`, `DataProcessor#parse`,
    `W3CDOMInfosetOutputter`, `InputSourceDataInputStream`) matches this
    project's best understanding of Daffodil's stable JAPI shape, but was
    not checked against the real `daffodil-japi_2.13:3.11.0` jar.
  - `oais-structure-kaitai`'s reflective walker relies on documented Kaitai
    Java-target conventions (no-prefix camelCase getters, `KaitaiStruct`/
    `KaitaiStream` base types, `ArrayList` for repeats); the demo format's
    "generated" class (`generated/Point2d.java`) is **hand-written to match
    what `ksc --target java` would produce**, not actually compiler output
    - regenerate it with the real compiler and delete the placeholder once
      you can.
  - `oais-structure-drb` was written to depend on DRB only via reflection
    specifically because its exact API could not be confirmed - see
    `oais-structure-drb/README-DRB.md` for exactly which method names it
    guesses and how to adjust them.

**Before relying on any of this**, run `mvn -q verify` from this directory
with real network access to Maven Central (and your own DRB jar added per
`oais-structure-drb/README-DRB.md`), and fix up whichever method names, if
any, your installed engine versions do not match. The `oais-structure-api`
module and the overall design should not need to change either way.

## Building

`oaiscore` - previously a separate `OAISIFBBCandidiate` clone you had to
`mvn install` by hand before this project would resolve it - is now its own
module in this reactor (`oaiscore/`, copied from that repository's
`oaisCore` project; see that module's `pom.xml` for exactly what was kept
and why). So building is just, from this project's root:

```sh
mvn install
mvn -pl oais-structure-demo exec:java
```

`mvn install` builds `oaiscore` first (Maven orders the reactor by the
dependency graph, regardless of `<modules>` order) and everything else
after it in the same run, and `mvn javadoc:aggregate -Ddoclint=none` picks
up `oaiscore`'s Javadoc along with every other module's.

To pick up further changes made in the original `OAISIFBBCandidiate`
checkout, copy its `oaisCore/src/main/java` tree over this project's
`oaiscore/src/main/java` (that checkout is otherwise no longer needed to
build this project).

## Extending

To bridge a fourth engine, add a module depending on `oais-structure-api`
and provide:

1. a `FormatSpecification` implementation carrying whatever configuration
   your engine needs;
2. an `ExecutableStructureRepInfo` (most simply, a subclass of
   `AbstractExecutableStructureRepInfo` implementing `doApply`) that runs
   your engine and returns a `StructureNode` - either by wrapping your
   engine's native result directly (as the DFDL/Kaitai/DRB adapters do) or
   by building `DefaultStructureNode`s;
3. a `StructureInterpreterProvider` and a
   `META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider`
   file naming it.

Nothing in `oais-structure-api` or the other adapter modules needs to
change.
