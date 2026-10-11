# DFRoot for Galaxy Tab S7 5G — `SM-T878U` / `gts7l`

**Status: no working root on this device, and no route to one.**
This branch is a compatibility and research record, not a root solution.

Fork of [`kurtulusakyuz/dfroot-s21`](https://github.com/kurtulusakyuz/dfroot-s21),
which ports [`diabl0w/DFRoot`](https://github.com/diabl0w/DFRoot)'s DirtyFrag
(CVE-2026-43284) ephemeral-root flow to the Galaxy S21 (`o1s`) and adds a working
non-GKI KernelSU LKM. **All of the S21 work is upstream's.** Everything in this
branch is the `SM-T878U` part, and it is deliberately small.

> [!WARNING]
> This does not root the Tab S7. What it does is make the manager say *why* it
> cannot, instead of failing silently.

## Target device

| | |
|---|---|
| Device | Samsung Galaxy Tab S7 5G `SM-T878U`, codename `gts7l` (Snapdragon 865+) |
| Android / firmware | Android 13, `T878USQS8DXE1` |
| Kernel | `4.19.113-27114284`, non-GKI Samsung vendor kernel, 2024-05-01 patch level |
| Bootloader | locked |
| SELinux | Enforcing |

## Status at a glance

| Route | Result |
|---|---|
| KernelSU late-load (`finit_module`) | **Not possible** |
| KernelSU Magica | **Not possible** — still terminates in `finit_module` |
| Custom kernel | **Not possible** — locked bootloader |
| Userspace UID 0 in the `shell` domain | Reachable via DirtyFrag, but adds very little |
| This branch | Firmware detection, honest UI, unit tests. No exploit, no LKM. |

## Why KernelSU cannot work here

### The module loader is removed, not disabled

`CONFIG_UH_LKM_BLOCK=y` is a Samsung patch that takes the module loader out of
the kernel. `__arm64_sys_finit_module` (`0xffffff800819786c`) is a rejection
stub: 280 bytes of code that checks `capable(CAP_SYS_MODULE)`, validates the ELF
header, and then returns an error on **every** path — `-EPERM`, `-EINVAL`, or
`-ENOEXEC` after `vfree()`. It never calls a loader. It even carries Samsung's
own message:

```
UH: LKM is not allowed by Samsung security policy.
```

Corroborating that, 18 loader symbols are absent from the 152,788-symbol table:
`load_module`, `do_init_module`, `check_modinfo`, `simplify_symbols`,
`apply_relocations`, `move_module`, `layout_and_allocate`, `complete_formation`,
`mod_sysfs_setup`, `module_sig_check`, `elf_validity_check`,
`copy_module_from_fd`, `add_unformed_module`, `resolve_symbol`, `unlink_module`,
`check_module_layout`, `find_module_sections`, `module_enable_nx`.

So there is no flag to flip. Even given arbitrary kernel read/write, the loader
would have to be reimplemented and re-injected.

### Module signatures are enforced

`CONFIG_MODULE_SIG=y`, `CONFIG_MODULE_SIG_FORCE=y`, `CONFIG_MODULE_SIG_ALL=y`,
`CONFIG_MODULE_SIG_HASH="sha512"`.

### Kernel text and rodata are read-only at runtime

`CONFIG_STRICT_KERNEL_RWX=y`, plus Samsung's EL2-level `CONFIG_UH_RKP=y`.
`sys_call_table` (`0xffffff8009e00758`) resolves into `.kernel` — the same
section as the kernel's text and rodata, which is what `STRICT_KERNEL_RWX` locks
down after init.

(`sig_enforce` at `0xffffff800b24a980` does land in `.kernel2`, the data section,
and stays writable — but writing it alone buys nothing, because the loader it
would gate no longer exists.)

### The bootloader is locked

Non-GKI KernelSU has to be compiled into the kernel and then booted. There is no
way to boot a custom kernel on this device.

## DEFEX: what a UID 0 process may actually do

`CONFIG_SECURITY_DEFEX=y`, with `CONFIG_DEFEX_KERNEL_ONLY` unset, so userspace
enforcement is on. DEFEX **kills** rather than returning `EACCES`, so a violation
surfaces only as `exit 137`, with no diagnostic of its own.

Measured on this firmware — same binary, only the path changed:

| Executed | as UID 0 | as UID 2000 |
|---|---|---|
| `/system/bin/{sh,toybox,id,app_process}` | OK | OK |
| byte-identical copy of `toybox` in a writable path | **SIGKILL** | OK |
| `/system/bin/linker64` (a *trusted* path) | **SIGKILL** | OK |
| `linker64 <writable-path PIE>` | **SIGKILL** | OK |
| a script in a writable path, read by a trusted `sh` | OK | OK |

So a UID 0 channel on this device must be script-based, never binary-based, and
cannot reach the dynamic linker — which closes the usual "let the linker do the
loading" bypass.

`app_process` itself starts, but ART fails to initialise as UID 0: `logcat` shows
`>>>>>> START com.android.internal.os.RuntimeInit uid 0 <<<<<<` and then nothing,
and the process exits with **`rc=0`** having done nothing. Root cause not
established; the same command works normally as UID 2000.

## What this branch changes

Upstream's manager treated every kernel as GKI — `Kernels.isGKI()` returned a
hard `true`, a backport hack for the S21 `o1s`/HZC2 build. That let every
non-GKI device inherit the S21 LKM exception and start a flow that could not
succeed. This branch replaces it with an explicit, testable check:

* **`KernelCompatibility`** (new, pure Java, no Android dependency) — keeps the
  tested `o1s`/HZC2 exception scoped to that exact device + release, restores the
  GKI version floor (≥ 5.10), and adds `isModuleLoadingBlocked()` matching the
  `SM-T878U` / `T878USQS8DXE1` / `4.19.113-27114284` profile above.
* **`JailbreakSupport`** (new) — the read-only eligibility check shared by the
  UI, the boot receiver and the app-zygote preload, so all three agree. It never
  spawns `getenforce` (or any other child) from app zygote, and fails closed when
  `/sys/fs/selinux/enforce` cannot be read: unreadable is not Permissive.
* **UI** — `HomeUiState` gains `isKernelSupported` / `jailbreakBlockReason`;
  `canJailbreak` replaces the bare `isSELinuxPermissive` test, and both UI modes
  show the actual reason instead of a generic "unsupported".
* **Guards** — `AppZygotePreload`, `BootCompletedReceiver` and `HomeScreen` abort
  with a message instead of starting a flow that cannot complete.
* **Settings** — `autoJailbreak` is clamped to `false` on unsupported kernels and
  the boot receiver is disabled; the ViewModel reads the clamped value back so
  the toggle cannot disagree with stored state.
* **Tests** — 4 unit tests over `KernelCompatibility`.

## Layout

* `app/` — Android stager app, built into `dirtyfrag.apk` (upstream).
* `dfmod/` — native stager sources (`df_stager.c`) and the `sud` root daemon (`sud.c`).
* `kernel-ksu/` — KernelSU LKM source (upstream).
* `manager/` — KernelSU manager app; this branch's changes live here.
* `lkm/` — legacy DirtyFrag module sources.
* `scripts/` — kernel build wrappers.
* `docs/` — screenshots.
* Not in the repo: `toolchains/`, `kernel-G991BXXSJHZC2/`.

## Build

```sh
./build.sh                 # stager APK -> ./dirtyfrag.apk
```

The manager is a separate Gradle build:

```sh
cd manager
JAVA_HOME=<jdk17+> ANDROID_HOME=<sdk> ./gradlew :app:assembleRelease
```

It needs `ANDROID_HOME` set — the repo intentionally carries no
`local.properties`. `./gradlew :app:compileDebugKotlin` is enough to check that
the Kotlin compiles; the pure-Java compatibility logic can be exercised without
Gradle at all:

```sh
javac -d out manager/app/src/main/java/me/weishu/kernelsu/KernelCompatibility.java \
            manager/app/src/test/java/me/weishu/kernelsu/KernelCompatibilityTest.java
java -cp out:<junit>:<hamcrest> org.junit.runner.JUnitCore me.weishu.kernelsu.KernelCompatibilityTest
```

## Upstream S21 status (inherited, unchanged here)

Everything below is `kurtulusakyuz/dfroot-s21`'s work and applies to the Galaxy
S21 `o1s` on `5.4.242-30958140-abG991BXXSJHZC2` — **not** to the Tab S7.

<img src="docs/ss.jpeg" width="270" alt="KernelSU working on S21">

* **Exploit → `sud` daemon** (`dfmod/sud.c`) — stateless root shell over
  `/dev/.sud`; each app-side call is one `sh -c` fork with output in `/dev/dfOUT`.
* **KernelSU LKM** (`kernel-ksu/`) — su redirect + allowlist grant, throne/manager
  fd-install, per-namespace apex CA injection, lazy module umount, SELinux-hide
  hooks. Samsung KDP/RKP/DEFEX/CFI-safe (tracepoint + task_work only, no text patch).
* **`mrun.sh`** — Magisk-style module runner (file-bind + dir-mirror mounts, CA
  staging, zygote/app-ns injection, `/system_ext/bin` su+busybox mirror).
* **`ksudshim.sh`** — manager-compatible `ksud` CLI over mrun.
* **Boot chain** — the exploit and load run only with the activity window open;
  background load risks a panic/Odin trip, so a foreground guard aborts `insmod`
  if the window closes.

The S21 port's kernel is non-GKI too, so it faces the same general problem — it
solves it by building the LKM into a kernel it is allowed to boot. That is the
step the Tab S7 cannot take.

## Known issues / limits

* Root exec of `/data` binaries is killed by DEFEX — including busybox as root.
  Busybox works as shell; root work uses `sh`/`toybox`. See the DEFEX table above.
* No Zygisk (LSPosed etc. install but never inject).
* No `overlayfs` tricks anywhere near `/system` (hardlockup → watchdog reset).
* Reboot wipes root on the S21 too — the LKM is loaded, never persisted.

## Related

* [`Wtrwx/smt878u-ionstack-poc`](https://github.com/Wtrwx/smt878u-ionstack-poc) —
  CVE-2026-43499 (IonStack/GhostLock) research on this same `SM-T878U`. Trigger
  and geometry verified; **no working root**.
* [KernelSU non-GKI integration notes](https://kernelsu.org/guide/how-to-integrate-for-non-gki.html)
  — the legacy support boundary (last upstream version: v0.9.5).

## Licence

Upstream's. See `kernel-ksu/LICENSE`.
