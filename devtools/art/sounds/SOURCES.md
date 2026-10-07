# Sound sources

The Huey's one sound is cut from a recording taken from freesound.org under the Creative Commons
Zero (CC0 1.0) public-domain dedication, which permits use, modification and redistribution without
attribution. The recordist is credited here anyway, because they deserve it. The file in `src/` is
the recording as downloaded (Freesound's high-quality Vorbis preview); `build.py sounds` cuts and
loops it into `src/main/resources/assets/huey/sounds/` with the shared `tools/sound/cutlib.py`.

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `156906-uh-1-helicopter.ogg` | UH-1 helicopter.wav | Rmutt | https://freesound.org/people/Rmutt/sounds/156906/ | CC0 1.0 |

| Shipped sound | Built from |
|---|---|
| `rotor.ogg` | 156906, from 72.812 s, 2.808 s long: thirty blade beats of 94 ms (a UH-1's two blades at 324 rpm beat at 10.8 a second) in the recording's loudest steady stretch (-17 dB within a decibel), the start chosen where the 200 ms crossfade's two ends correlate best (0.72) at equal level, so the beat runs on through the join |
