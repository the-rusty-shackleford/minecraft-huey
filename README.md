# Huey

A life-size Bell UH-1H for [Rotorcraft](https://github.com/the-rusty-shackleford/minecraft-rotorcraft),
the helicopter protocol layered on [Vanilla Wheels](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels),
on NeoForge 1.21.1. One block a metre: 12.6 blocks nose to tail, a two-bladed rotor 14.7 across, a
tail rotor on the fin's left. A pilot (the right-hand seat, as in the Army's Hueys), a copilot and
six on red troop seats in an open cabin, the cargo doors slid back; skids; a cargo hook under the
belly for a sling load; a mount for the crop sprayer. There is no Java in it: the helicopter is two
datapack profiles, a Blockbench mesh and a sound, and the protocols do the rest, so flying, the
sling and the sprayer are documented in Rotorcraft's README and the vehicle's keys, fuel, paint and
repairs in Vanilla Wheels'.

## Getting one

- **Chassis**: three glass panes over six steel blocks (`G G G / B B B / B B B`).
- **Build**: the chassis and an engine in a Mechanic Lift, and Build. Skids need no wheels. It
  comes out olive drab; paint it there with a dye.

## Flying it

Rotorcraft's keys: Space climbs, Left Shift descends, R gets out (within three blocks of the
ground), G hooks and lets go a sling load, V switches the crop sprayer, H the lights. The rotor
spools up for three seconds before it lifts. Nobody aboard is ever hurt by flying; crashes wear the
helicopter. In third person the camera stands 16 blocks behind the pilot's eye (the profile's
`camera`; Vanilla Wheels' length rule would put it 20 back, the Huey a speck in the middle of the
screen), five behind the tail and over it.

Numbers: 1.4 blocks a tick at the top, 0.4 reverse; climbs 0.4 and descends 0.5 a tick; turns 3
degrees a tick; tilts up to 12 degrees; 36 000 ticks of fuel; repaired with steel ingots, 24 for a
wreck. The landing light and searchlight under the nose light the ground 16 blocks ahead.

## How it is made

`devtools/art/build.py` writes everything: the model (`huey.bbmodel`, cubes in named folders on a
generated texture of one texel a model pixel), both profiles, the recipe and its unlock, the lang,
`sounds.json` and the gametests' pad. Run from the repository root:

```
uv run --no-project python devtools/art/build.py
uv run --no-project --with numpy python devtools/art/build.py sounds    # needs ffmpeg
```

The model is built in metres from the UH-1H's published dimensions, its side view checked against
Greg Goebel's public-domain profile (`devtools/art/reference/`, with `SOURCES.md`). The origin is on
the ground under the mast, so it lands on its skids and turns about its rotor; its `hull` (six
boxes: cabin, cowling, skids, boom, elevator, fin) is what meets the world in the air, and the
rotors' radii keep it drawn while only a blade is in view. The windshield, the corner windows and
the rounded roof edge meet along the windshield's slant a pixel at a time, under a pillar at each
corner: glass cut square against a slant leaves a triangle of nothing at every band. The folders the
profiles select by: `paint` (dyed), `glass` (drawn translucent), `cockpit` (the windshield's post,
hidden from riders' own eyes), `rotor_main` and `rotor_tail` (spun by Rotorcraft), `spray_boom`
(drawn while a sprayer is fitted), `lenses` (lit with the lights), `needle_speed` and `needle_fuel`
(the airspeed and fuel gauges in front of the pilot). The rotor loop is cut from a CC0 recording of
a UH-1 (`devtools/art/sounds/SOURCES.md`); its note runs from 0.62 spooling up to 1.04 at full
speed, a rotor's, not a car's.

The shared tools it uses live beside the repo in `minecraft mods/tools/`: `bbgen` (the Blockbench
writer and an offline renderer for judging the model) and `sound` (the loop cutter).

## Verifying it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew clean build -PskipBooth
```

Rotorcraft and Vanilla Wheels come from Maven Local (`./gradlew publishToMavenLocal` in each, Vanilla
Wheels first). Five gametests on a 48-block pad: the profiles make it an aircraft of eight seats on
skids; the chassis crafts; the lift builds and paints it; it climbs, crosses the pad, turns and
lands softly with nobody hurt; and it carries the Sling Container on its hook with a cow and apples
aboard and sets it down whole.

## License

AGPL-3.0-or-later. Copyright Rusty Shackleford and nfx.
