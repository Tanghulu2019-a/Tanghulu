# Tanghulu source and license notices

Tanghulu is the author and primary contributor of this mod.
Cqb13 (cqb13) and Mikumiku (mikumiku) are authors of upstream source used
for some features; this does not imply direct participation or endorsement.
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

Modified by Tanghulu on 2026-10-24: privacy cleanup, release version unification,
and removal of the Stash integration and associated navigation, localization,
HUDs and record-cleaning hooks. Also removed the inherited waypoint adapter
and its map-writing call sites, and an unused block-ESP implementation with
substantial overlap with Stash source. Locator search results now use chat
coordinates instead of that waypoint adapter.

Earlier private working copies included modified code from:
https://github.com/miles352/meteor-stashhunting-addon/releases/tag/0.16.7
That source and its identified derived integration code are excluded from this
release because a redistribution license could not be confirmed. Mentioning
its name records removal history and does not assert permission to distribute
its code. No Stash module, HUD, source subtree or integration mixin is shipped.

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
Release reference verified on 2026-10-24: mc1.21.11-v2.6.24,
commit 063a8ed8383cd621bda0e7e47a09065fa4bf291e:
https://github.com/cqb13/Numby-hack/tree/063a8ed8383cd621bda0e7e47a09065fa4bf291e
The bundled Numby license is byte-identical to LICENSE at that revision
(Git blob 6c6b49e94f05a5f393b6b250df775a3089639c22).
Integrated Beyblade (README: Bayblade), Confetti, SignTextEsp, SafeFire,
NewChunks and TotemParticleMixin on 2026-10-24. Changes include Tanghulu
registration, Chinese module names/settings/descriptions/enum labels, rotation
corrections, and client-thread chunk parsing with world-scoped cache cleanup.
Upstream file credits retained: FloRida from venomhack; Confetti from Tanuki;
SignTextEsp by cqb13; SafeFire from Tanuki, originally by walaryne;
NewChunks from BleachHack by BleachDrinker420, modified by cqb13,
with packet-loading credit to Trouser-Streak. See licenses/Numby-Hack-GPL-3.0.txt.
