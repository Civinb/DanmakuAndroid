# Bilibili Danmaku Viewer (Android)

[简体中文](README.md) | English

This danmaku viewer is meant for times when the video itself isn't that important but you still want to see how viewers react in the danmaku (bullet comments).
It shows Bilibili live / video danmaku as a list in a floating window, used alongside the official Bilibili app.


## Features

- **Live danmaku**: start from a room number, link, or share; Super Chats, gifts, and entry messages can each be toggled; hide live-room emotes; automatic reconnection after disconnects
- **Video danmaku**: BV ID / link, multi-part videos supported; automatically follows Bilibili's playback progress, or control it manually; switches danmaku automatically when you change parts
- **Floating window**: drag, resize, lock with touch pass-through, collapse, jump to latest, adjustable style; blackout background (tap the black area to bring up playback controls)
- **Filters**: block by keyword, regex, or user; show-only mode; merge duplicate danmaku
- **Other**: optional QR-code login, in-app update check
- **Note**: when watching video danmaku, the full danmaku is only shown after logging in; without logging in you get only about 10%–20% of the danmaku
## Usage

1. Download and install from [Releases](https://github.com/Civinb/DanmakuAndroid/releases), and grant the floating-window (display over other apps) permission
2. Live: enter a room number or link on the home screen → Connect
3. Video: enter a BV ID or link on the home screen → Load → choose a part → Start, then play the same video in the Bilibili app (automatic sync requires enabling "Notification access")
4. Both live and video support quick start from the live room or video page: tap Share → More → 弹幕机 (recommended)


## Disclaimer
- This project was built entirely with Claude Code
- This project is for personal study and use only. Bilibili's APIs may change at any time and break functionality.
