---
layout: default
title: Home
nav_order: 1
---

# Pudding

Pudding is a music player that let you enjoy music from various platforms!

[Download latest][get-latest-apk]{: .btn .btn-primary }

or [download other version][get-old-apk].

## How to install Pudding

Pudding is currently only available on Android.  You can download the latest APK file at [here][get-latest-apk], or find other versions at [here][get-old-apk].

## Supported platforms

 + Local
 + YouTube

[get-latest-apk]: javascript:getLatestApk();

[get-old-apk]: javascript:getOldApk();

<script>
    function getLatestApk(){fetch("https://api.github.com/repos/BorisChen396/PuddingPlayer/releases/latest").then(res=>{if(res.ok)res.json().then(json=>{window.location.href=json.assets[json.assets.length-1].browser_download_url})})};
    function getOldApk(){if(confirm("Old versions may be unusable because of bugs or other problems.\nContinue?"))window.location.href="https://github.com/BorisChen396/PuddingPlayer/releases"};
</script>
