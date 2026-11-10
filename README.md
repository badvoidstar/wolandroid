# WOLAndroid

A native Android Wake-on-LAN app for Android 8.0 and newer. No account, server,
analytics, advertising, root access or background service is required.

## Install

After a successful build of `main`, open this repository's **Releases** page,
download `WOLAndroid-<version>.apk` to your phone, and open it. Android will ask
you to allow installation from the browser or file manager you used. This is an
APK, not an app bundle; it can be installed directly without Google Play.
The same APK is also retained in the main workflow's **Artifacts** for 90 days.
Release assets remain downloadable until the release is deleted.

**Signing matters:** without signing secrets, CI produces an installable,
development-signed APK with a new key on each fresh runner. Updating an existing
installation from a different key requires uninstalling first, which deletes
saved devices and invalidates their shortcuts. Configure a stable private
release key (below) before relying on upgrades. No private key is checked in.

## Use

Opening the app normally opens its device list and starts discovery. An active
VPN takes priority over Wi-Fi. The subnet button selects a routed subnet or lets
you enter a remote IPv4 LAN range, such as `192.168.1.0/24`.

The list has **Device Name | MAC address | IP address** columns. Swipe vertically
anywhere in a row: Android's native `ListView` provides velocity-based scrolling,
flinging and natural deceleration. Double-tap a row to send three standard
102-byte magic packets to UDP port 9. Holding a row opens wake, shortcut, edit and
forget options. TalkBack's standard item activation wakes the selected device.

**Add device** and **Edit device** let you supply a MAC from your router or the
target's network settings. Discovered and manually entered devices are retained
per subnet, including devices that later go to sleep. Entries distinguish
observations from saved/neighbor-cache information. Saved MACs are explicitly
marked because they cannot necessarily be verified again. Scanning can be
stopped, and stops when the UI leaves the foreground.

To create a home-screen shortcut, hold a device, select **Create home screen
shortcut**, and accept your launcher's pin request. Each shortcut stores its own
snapshot of the device's MAC, IP and subnet in private app storage. Editing or
forgetting a list entry does not retarget an existing shortcut; recreate the
shortcut if the target or its subnet changes.

A shortcut opens only a transparent, non-interactive wake activity in a separate,
recents-excluded task. It sends on a worker thread, closes its socket and executor,
then finishes and removes that task. It never opens the list, starts a service or
schedules continuing work. Successful shortcut activation has no app UI; errors
use a brief Android toast. Android may retain an idle cached process, as it does
for any app; applications cannot promise OS-level process termination.

## Network realities

No unprivileged Android app can guarantee discovery of every host, DNS name and
MAC on an arbitrary Wi-Fi network or VPN:

* Discovery probes all host addresses in the selected IPv4 range with up to 32
  concurrent workers. TCP connections or connection refusals on ports 80, 443,
  22 and 445 are evidence of a responding host. Firewalls, client isolation,
  packet loss and sleeping devices can prevent discovery. Old ARP entries are
  cached evidence, not proof a device is online.
* PTR lookups use the selected network's DNS servers, followed by a unicast mDNS
  reverse lookup. Devices without published names show **Name unavailable**.
  The phone's model is used as a fallback label for the phone itself.
* Android 10+ restricts `/proc/net`, preventing ordinary apps from reading other
  devices' ARP/MAC entries. This app deliberately does not bypass that restriction.
  Android 8/9 can use accessible ARP entries; otherwise MACs show **Unavailable**
  until entered manually. A layer-3 VPN does not carry remote Ethernet MACs.
* VPN discovery uses the VPN's IPv4 routes, DNS and bound sockets. A full-tunnel
  default route cannot reveal the remote LAN's actual prefix: enter it manually.
  VPN access does **not** imply broadcast support. The VPN/router must forward
  subnet-directed broadcasts to the target LAN, or provide a suitable layer-2
  tunnel. Many consumer VPNs cannot support remote WOL without a LAN relay.
* Wi-Fi wakes use the actual interface prefix's broadcast address, not an assumed
  `/24`. VPN wakes use the saved target subnet's directed broadcast. `/31` and
  `/32` point-to-point ranges use the target's unicast IP instead; waking via
  unicast can fail once router neighbor entries expire.
* Enable WOL in the target firmware and network adapter. A successful UDP send is
  not acknowledgement of receipt or proof that the device woke. Many Wi-Fi
  adapters do not support waking from shutdown.
* IPv6 is not scanned. Subnets above 4,096 hosts are rejected with an explicit
  instruction to choose a narrower range; they are never silently truncated.
  Saved entries are scoped by CIDR, not Wi-Fi identity: verify cached MACs when
  changing between networks with overlapping addresses or after DHCP changes.

Only scan networks you own or have permission to scan. The app requires only
`INTERNET` and `ACCESS_NETWORK_STATE`, not location or contact access. It targets
Android API 36; newer Android versions or device-specific network policy may
restrict local-network access. See the in-app help for the same limitations.

## GitHub CI/CD

`.github/workflows/android.yml` runs for pushes to **all branches**, pull requests
and manual dispatches. Every run provisions JDK 17 and Android SDK 36, then uses
the Gradle 9.9.0 wrapper to run the same release unit tests, Android lint,
release APK build, APK signature verification and alignment check.

Only non-PR runs on `main` retain the APK and publish a versioned GitHub release,
including an SHA-256 checksum. Merging into `main` triggers this automatically;
direct main pushes and manual main builds also publish. Non-main/PR runs exercise
the packaging pipeline but upload no APK artifact and create no release.
The publishing job alone receives `contents: write`. PR builds never receive
release-signing credentials.

Enable Actions in the repository and permit the workflow's requested release
write permission. If an organization policy forbids release creation, the main
build artifact remains available but the publish job will fail explicitly.

### Stable release signing

Generate and safely back up a private Android signing key on a trusted machine:

```sh
keytool -genkeypair -v -keystore wolandroid-release.jks -alias wolandroid \
  -keyalg RSA -keysize 3072 -validity 10000
```

In **Settings > Secrets and variables > Actions**, configure all four repository
secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing alias, e.g. `wolandroid` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Use your platform's base64 tool to encode the keystore without adding content to
the repository. Keep the key and passwords private and backed up. Partial secret
configuration fails the workflow instead of silently selecting another key.
Release signing is used only for main builds, with the temporary decoded key
removed in an always-run cleanup step. Branches use development signing.

### Local development

Use JDK 17 and Android SDK platform 36/build-tools 36.0.0.
Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir=...`.
The checked-in wrapper downloads Gradle 9.9.0. The build uses Android Gradle
Plugin 9.5.1, JUnit 4.13.2 and Robolectric 4.17; dependency updates use stable
releases rather than preview versions.

```sh
./gradlew --no-daemon :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The default release variant uses local Android development signing, so it is
sideloadable immediately. To sign locally with your private key, supply
`WOL_KEYSTORE_FILE`, `WOL_KEYSTORE_PASSWORD`, `WOL_KEY_ALIAS` and
`WOL_KEY_PASSWORD` as environment variables. CI derives increasing version codes
and version names from its run number; locally they default to `1` / `1.0.0` and
can be overridden with `VERSION_CODE` / `VERSION_NAME`.

Unit tests cover packet bytes, MAC validation, subnet boundaries, DNS decoding
(including malformed replies), ARP filtering, cached-device merging, gestures
and durable shortcut targets. For hardware acceptance, use a WOL-capable sleeping
computer on your Wi-Fi, verify the UDP broadcast in a packet capture, confirm
double-tap wake and swipe/fling behavior, then pin and launch a shortcut after
closing the app. Repeat over a VPN that explicitly forwards directed broadcasts.
Also test network switching, a device without PTR records and an unavailable MAC.
