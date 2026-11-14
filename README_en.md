# Bilibili Danmaku Viewer (Android)

[简体中文](README.md) | English

This danmaku viewer is meant for times when the video itself isn't that important but you still want to see how viewers react in the danmaku (bullet comments).
It shows Bilibili live / video danmaku as a list in a floating window, used alongside the official Bilibili app.


## Features

- **Live danmaku**: Super Chats, gifts and entry messages can each be toggled; hide live-room emotes; automatic reconnection after disconnects
- **Video danmaku**: automatically follows Bilibili's playback progress, or control it manually; switches danmaku automatically when you change parts in Bilibili
- **Floating window**: drag, resize, lock with touch pass-through, collapse, jump to latest, adjustable style; remembers position and size separately for portrait and landscape; blackout background (tap the black area to bring up playback controls)
- **Filters**: block by keyword, regex, or user; show-only mode; merge duplicate danmaku
- **Other**: optional QR-code login (with a reminder when it expires), in-app update check, Chinese / English UI
- **Note**: when watching video danmaku, the full danmaku is only shown after logging in; without logging in you get only about 10%–20% of the danmaku

## Usage

1. Download and install from [Releases](https://github.com/Civinb/DanmakuAndroid/releases), and grant the floating-window (display over other apps) permission
2. Enter a live room number, BV ID or link on the home screen → Start; live or video is detected automatically
3. For videos, play the same video in the Bilibili app and the danmaku will sync (automatic sync requires enabling "Notification access")
4. Recommended: in a Bilibili live room or video page, tap Share → More → Danmaku Viewer (弹幕机) to start directly


## Disclaimer
- This project was built entirely with Claude Code
- This project is for personal study and use only. Bilibili's APIs may change at any time and break functionality.
