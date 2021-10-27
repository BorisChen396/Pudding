---
layout: default
title: Home
nav_order: 1
---

# Pudding

Pudding is a music player that let you enjoy music from various sources!

[Download latest][get-latest-apk]{: .btn .btn-primary } [Download other version][get-old-apk]{: .btn }

## Installation

Pudding currently only works on Android Lollipop or above.  You can download the latest APK file [here][get-latest-apk], or find other versions [here][get-old-apk].

## Supported sources

 + Local
 + YouTube
More sources will be supported in the future!

[get-latest-apk]: javascript:getLatestApk();

[get-old-apk]: javascript:getOldApk();

<script>
 function getLatestApk(){fetch("https://api.github.com/repos/BorisChen396/PuddingPlayer/releases/latest").then(res=>{if(res.ok)res.json().then(json=>{window.location.href=json.assets[json.assets.length-1].browser_download_url})})};
 function getOldApk(){if(confirm("Old versions may be unusable because of bugs or other problems.\nContinue?"))window.location.href="https://github.com/BorisChen396/PuddingPlayer/releases"};
</script>
