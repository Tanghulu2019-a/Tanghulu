# Tanghulu source and license notices

Tanghulu2019 is the author and primary contributor of this mod.
Some features derive from source by Cqb13 (cqb13), Mikumiku (mikumiku),
and jefff; source credits do not imply direct collaboration or endorsement.
Upstream file-level credits and license notices are retained.

Tanghulu includes modified code from Meteor Miku by mikumiku:
https://github.com/mikumiku7/meteor-miku

Meteor Miku declares GNU GPL version 3 in its LICENSE and README. Its LICENSE
was inspected at commit 29b9f74494f951726174fd885b323f95aa62978e:
https://github.com/mikumiku7/meteor-miku/blob/29b9f74494f951726174fd885b323f95aa62978e/LICENSE
That commit is a license-inspection reference, not a claim that the supplied
trimmed source distribution exactly matches that revision.

The supplied Meteor Miku 11.0 trimmed v4 and Ancient Apple Hunter 0.1.19 sources
were integrated on 2026-10-18 with namespace, entrypoint and compatibility
changes. Ancient Apple Hunter attribution: Tanghulu. Existing file-level
credits and third-party license notices are retained.

Stash integration for Tanghulu 1.0.2, modified on 2026-10-25:
Upstream project: meteor-stashhunting-addon / jeff mod by miles352 (jefff).
Release reference: https://github.com/miles352/meteor-stashhunting-addon/releases/tag/0.16.7
Tag resolved during this review to c88c63c268705b1f23ac1085cefa70b6f24be906.
This is a provenance reference, not a claim of a byte-identical upstream import.
The supplied source was adapted from the maintainer's local distribution.
Five modules and two HUDs are included; VanityESP credits xqyet.
The upstream README also credits xqyet for AFKVanillaFly, which is excluded.
Retain all existing file-level credits and the upstream README in docs/stash-upstream.

No LICENSE file was present in the inspected upstream 0.16.7 source tree.
On 2026-10-25 the Tanghulu maintainer expressly confirmed obtaining permission
covering modification, public source redistribution and distribution with this
GPL-3.0-only project, including third-party portions of the imported code.
This records the maintainer's authorization statement; it is not an upstream
public license, independent verification of a private grant, or endorsement.

Modifications: dev.tanghulu namespace, Chinese UI and settings, optional
dependency registration, isolated flight hooks, Baritone travel lifecycle,
stash CSV/JSON handling and current-world cleanup. Durability and coordinate
safety checks move from AutoLog to AutoLogPlus in 1.0.2, disabled by default.
XaeroPlus is compile-only and is not bundled. Existing privacy cleanup remains.

The retained project is distributed under GPL-3.0-only; see LICENSE. No claim
of sole authorship over upstream code is made. Complete corresponding project
source and build scripts are provided with the binary release.

AncientCityGenerator and SeedFinding MIT notices are retained in
ancient/licenses and included in the runtime JAR. Historical Cubiomes provenance
is retained for reference; Cubiomes is not included in the current runtime or
public source package. External mod JARs used for compilation are fetched
separately from pinned official distribution URLs with SHA-256 verification;
see build-support/dependencies/downloads.json.

Numby Hack v2.6.24 (cqb13), supplied mc1.21.11 source archive, GPL-3.0:
https://github.com/cqb13/Numby-hack
Integrated Beyblade (README: Bayblade), Confetti, SignTextEsp, SafeFire,
NewChunks and TotemParticleMixin on 2026-10-24. Changes include Tanghulu
registration, Chinese module names/settings/descriptions/enum labels, rotation
corrections, and client-thread chunk parsing with world-scoped cache cleanup.
Upstream file credits retained: FloRida from venomhack; Confetti from Tanuki;
SignTextEsp by cqb13; SafeFire from Tanuki, originally by walaryne;
NewChunks from BleachHack by BleachDrinker420, modified by cqb13,
with packet-loading credit to Trouser-Streak. See licenses/Numby-Hack-GPL-3.0.txt.
