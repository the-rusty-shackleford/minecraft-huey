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

## Status (2026-10-07): built and gated; not yet seen in the game

- Gate: 5 GameTests green (no JUnit: no Java beyond the tests).
- Built: the model, both profiles (with Rotorcraft's hull and rotor radii), the chassis recipe and
  unlock, the lang, the rotor loop.
- Not done: the booth (a rendering client: not while Rusty's own game is open), the wiki page and
  its images (from the booth), Rusty's review of the model, the playtest on the 4070.
- Nothing released; no GitHub repo yet (created at release on Rusty's word). Ships with Vanilla
  Wheels 1.12.0, Rotorcraft 1.0.0 and the Chinook as pack 1.75.0.

## Shape

`devtools/art/build.py` writes every file under `src/main/resources` and the gametests' pad; edit
it, never the outputs. The model is judged by rendering it with `../tools/bbgen/render.py`
(`--bounds` lays it over a reference at a fixed frame; `--look` and a zoom close in on a joint).
