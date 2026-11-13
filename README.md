# LifeStreamerV2

LifeStreamer is an Android app designed for IRL live streaming.

For original (stable) LifeStreamer (version 1) go here https://github.com/dimadesu/LifeStreamer.

LifeStreamerV2 (major version 2.x.x) repo is a place to take risks and try crazy experiments without affecting V1.

## LifeStreamerV2 new features on top of V1

- Reimagined UI:
  - Main screen buttons grouped into categories.
  - Separate settings screen removed.
  - All settings are available on the main screen.
- Dual camera support for Android cameras with picture-in-picture (PIP) and split view layouts.
- Extra options for Android camera video source:
  - Stabilisation: OIS/EIS/OFF.
  - Noise reduction.
- Switching from Android camera to Android camera improvements: reduce stuttering and frame freezes during transition, render black frames.
- Android camera video source: no more video/audio stuttering or interruptions on published stream when app goes to/from background.

## The vision for V1

Original LifeStreamer (V1) is supposed to be as small as possible, to be very stable and reliable.

I was always hesitant to endlessly add more features to it.

LS V1:
- Inputs: various video/audio sources
- Input switcher
- Video/audio monitor
- Outputs: RTMP/SRT/SRTLA with adaptive bitrate.

A lot of people already use it and, in a way, it's perfect and complete as it is. It is sort of in a maintenance mode. I don't want to add any major changes to it for now.

I plan to do a long term maintenance on V1 and keep updating it when new Android versions come out. It's easier to manage (test and fix) when there is less code and features overall.

## The vision for V2

For LS V2 I want to take apart V1 and reimagine UI and make room for more features.

I want to add and remove features and try things without breaking anything in version 1.

LS V2 ideas:
- Take things apart, make them modular and reassamble in new ways.
  - Cleanup code by building a solid core.
  - Write tests.
  - Reduce complexity where possible.
  - Break down the code into pieces that have minimal dependcies.
- Try some risky improvement ideas that are usually not worth the added complexity. Contradicts previous items about cleanup. Experiements often make code more messy, but it's good to experiment.
- Reimagine UI and settings
  - Try multiple alternative UIs (layouts).
  - Web server UI (kinda like Belabox has).
- Process video:
  - Video composition (combining multiple video sources in one screen). Picture-in-picture (PIP), split screen.
  - Extend image (bitmap) source.
  - Overlays.
  - Video filters.
- Etc.

## GitHub releases

I want to have a place to store these experimental V2 releases (.apk files) without polluting main V1 project.

That's why this repository exists. I'll publish V2 .apk files here as GitHub releases.

You can have both V1 and V2 apps installed at the same time. They should be independent (for the most part) and not affect each other.

- Open https://github.com/dimadesu/LifeStreamerV2/releases on the device that you want to install it on
- Download .apk file
- Install
- Enjoy!

## The future

Let's start here and see how it goes.

Maybe this can become a completely new app.

Maybe some ideas from here can be added to V1.

Maybe someday this will replace V1.

Not sure yet.
