---
title: Huey — project
type: overview
layer: store
tags: [overview]
---

# Huey

## What this is

A life-size Bell UH-1H for Rotorcraft (Rusty, 2026-10-06): data only, nesting Rotorcraft (which
nests Vanilla Wheels). The plan is `~/.claude/plans/i-want-to-add-curious-locket.md`; D-0001 is the
model, the origin and the sound.

## Status: 1.1.0 released 2026-10-07 in pack 1.76.0

- **1.1.0, the baggage store** (D-0002; Rusty, after flying it: storage "like the cars", "a double
  chest would be nice"): six rows hidden in the boom, a hatch on each side, a hit box over them; the
  inventory key opens it from a seat. Rusty saw the photos and said "release it as pack 1.76.0":
  tag `v1.1.0` at `22af99f` (the gate ran on `bdf0a21`, which differs only in the wiki images), jar
  sha1 `9d7da903` on GitHub and on the server (the server repo's `knowledge/releases/pack-1.76.0.md`).
  Not yet seen: anyone opening the store on the box.

- Gate: 5 GameTests and the booth (10 checks) green (no JUnit: no Java beyond the tests).
- Built: the model, both profiles (with Rotorcraft's hull and rotor radii), the chassis recipe and
  unlock, the lang, the rotor loop, the booth, the wiki page.
- Rusty passed the booth photos on 2026-10-07 ("Looks good"), after the cockpit glass was made to
  meet the slant and the third-person cameras were set. Not done: the playtest on the 4070.
- Released 2026-10-07 in pack 1.75.0 on Rusty's "looks good, fix the latent key bug then release"
  (the server repo's `knowledge/releases/pack-1.75.0.md`): public repo created then, the jar's sha1
  `2402f6ea` on GitHub and on the server. Not yet seen: anyone flying it on the box. Rusty flew it
  in the 4070 playtest before the release.

## Shape

`devtools/art/build.py` writes every file under `src/main/resources` and the gametests' pad; edit
it, never the outputs. The model is judged by rendering it with `../tools/bbgen/render.py`
(`--bounds` lays it over a reference at a fixed frame; `--look` and a zoom close in on a joint).
