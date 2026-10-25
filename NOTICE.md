# Tanghulu source and license notices

Tanghulu2019 is the author and primary contributor of this mod.
Cqb13 (cqb13) and Mikumiku (mikumiku) contributed source code for some features.
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

Restored on 2026-10-24 from the user-supplied Tanghulu 1.0.0 source into
1.0.1, following the user's statement that authorization for the relevant
Stash code was obtained from its author. This records the supplied provenance;
no additional upstream license terms are invented here.
Upstream: https://github.com/miles352/meteor-stashhunting-addon/releases/tag/0.16.7
Stash author: jefff. AFKVanillaFly and VanityESP credit xqyet.
Seven modules, two HUDs, localization, optional flight hooks and stash record
clearing are restored. Duplicate AutoLog safety checks reuse the existing
1.0.1 implementation. XaeroPlus is an optional compile-only dependency and is
not bundled in the addon. Existing 1.0.1 features and privacy cleanup remain.

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
