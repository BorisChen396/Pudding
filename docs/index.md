# Pudding

Pudding is a music player that let you enjoy music on your device and YouTube!

[Download latest APK][get-latest-apk]{: .btn }

[Download older APK][get-latest-apk]{: .btn }

## How to install Pudding

Pudding is currently only available on Android.  You can download the latest APK file at [here][get-latest-apk], or find other versions at [here][get-old-apk].

[get-latest-apk]: javascript:getLatestApk();

[get-old-apk]: javascript:getOldApk();

<script>
    function getLatestApk(){fetch("https://api.github.com/repos/BorisChen396/PuddingPlayer/releases").then(res=>{if(res.ok)res.json().then(json=>{alert(JSON.stringify(json))})})};
    function getOldApk(){if(confirm("Old versions may be unusable because of bugs or other problems.\nContinue?"))window.location.href="https://github.com/BorisChen396/PuddingPlayer/releases"};
</script>
