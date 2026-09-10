"""Build a reproducible mod-manager ZIP after gradlew clean build. Python 3.10+, no dependencies."""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
PREFIX = "OmegaAI/"
FILES = [
    "mod_info.json", "omega_ai.version", "jars/OmegaAI.jar",
    "graphics/omega_ai_icon.png", "data/config/settings.json", "data/config/LunaSettings.csv",
    "data/config/LunaSettingsConfig.json",
    "data/config/version/version_files.csv", "data/missions/mission_list.csv",
    "data/missions/omega_ai3_trial/descriptor.json", "data/missions/omega_ai3_trial/mission_text.txt",
    "data/missions/omega_ai3_trial/icon.png",
]
REPO = "https://github.com/andrzejsokolowski/starsector-omega-ai-3"
RAW = "https://raw.githubusercontent.com/andrzejsokolowski/starsector-omega-ai-3/main/omega_ai.version"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def source_digest() -> str:
    paths = list((ROOT / "src").rglob("*.java"))
    paths += [ROOT / p for p in ["build.gradle", "settings.gradle", "gradle.properties", "mod_info.json"]]
    digest = hashlib.sha256()
    for path in sorted(paths, key=lambda p: p.relative_to(ROOT).as_posix()):
        digest.update(path.relative_to(ROOT).as_posix().encode() + b"\0" + path.read_bytes() + b"\0")
    return digest.hexdigest()


def read_manifest(data: bytes) -> dict[str, str]:
    unfolded = data.decode("utf-8").replace("\r\n ", "").replace("\n ", "")
    return dict(line.split(": ", 1) for line in unfolded.splitlines() if ": " in line)


def inspect_resources(payload: dict[str, bytes]) -> list[str]:
    def needed(name: str) -> bytes:
        require(name in payload and bool(payload[name].strip()), f"Missing or empty runtime resource: {name}")
        return payload[name]

    mod = json.loads(needed("mod_info.json"))
    luna = json.loads(needed("data/config/LunaSettingsConfig.json"))
    icon_path = luna.get(mod["id"], {}).get("iconPath")
    require(isinstance(icon_path, str) and bool(icon_path), "Register the LunaLib icon under the actual mod ID.")
    needed(icon_path)
    # Starsector reads mission_text.txt during resource loading, even before a mission is played.
    registrations = list(csv.DictReader(io.StringIO(needed("data/missions/mission_list.csv").decode())))
    require(bool(registrations) and "mission" in registrations[0], "The mission CSV registration is incorrect.")
    missions = []
    for row in registrations:
        mission_id = row["mission"]
        require(bool(mission_id) and all(c.isalnum() or c == "_" for c in mission_id), "Invalid mission ID.")
        folder = f"data/missions/{mission_id}/"
        descriptor = json.loads(needed(folder + "descriptor.json"))
        needed(folder + "mission_text.txt").decode("utf-8")
        needed(folder + descriptor["icon"])
        require(descriptor["difficulty"] in ["EASY", "MEDIUM", "HARD", "IMPOSSIBLE"], "Use a standard mission difficulty.")
        missions.append(mission_id)
    require(len(missions) == len(set(missions)), "The mission CSV contains duplicate entries.")
    return missions


def inspect_forum_metadata(mod: dict, version_file: dict) -> None:
    topic, version_topic = mod.get("modThreadId"), version_file.get("modThreadId")
    if topic is None and version_topic is None:
        return  # The author will create the forum topic after further development.
    require(isinstance(topic, str) and topic.isdecimal() and int(topic) > 0
            and type(version_topic) is int and int(topic) == version_topic,
            "Set the same real forum topic as a string in mod_info.json and a number in the version file.")


def inspect_payload(payload: dict[str, bytes]) -> str:
    missions = inspect_resources(payload)
    require(set(payload) == set(FILES), "The archive contains missing or unexpected files.")
    for name, content in payload.items():
        if name.endswith((".json", ".csv", ".version")):
            require(not content.startswith(b"\xef\xbb\xbf"), f"Remove the UTF-8 BOM from {name}.")
        if name.endswith((".json", ".version")):
            json.loads(content)
    mod = json.loads(payload["mod_info.json"])
    version_file = json.loads(payload["omega_ai.version"])
    version = mod["version"]
    v = version_file["modVersion"]
    require(version == f"{v['major']}.{v['minor']}.{v['patch']}", "The mod versions do not match.")
    require(mod["id"] == "omega_ai3", "The mod ID must remain separate from earlier Omega AI projects.")
    require(mod["gameVersion"] == version_file["starsectorVersion"], "The game versions do not match.")
    require(mod["updateCheckURL"] == version_file["masterVersionFile"] == RAW, "The update URLs do not match.")
    require(version_file["directDownloadURL"] == f"{REPO}/releases/download/v{version}/Omega-AI-{version}.zip",
            "The download URL does not name the release ZIP.")
    require(list(csv.reader(io.StringIO(payload["data/config/version/version_files.csv"].decode())))
            == [["version file"], ["omega_ai.version"]], "The version CSV registration is incorrect.")
    inspect_forum_metadata(mod, version_file)
    defaults = json.loads(payload["data/config/settings.json"])
    require(defaults["omega_ai3"]["mode"] == "Observe", "This alpha must start in Observe mode.")
    require(defaults["plugins"]["omega_ai3_combat"] == "omegaai3.OmegaCombatPlugin", "The combat plugin is not registered.")
    require(list(csv.reader(io.StringIO(payload["data/missions/mission_list.csv"].decode())))
            == [["mission"], ["omega_ai3_trial"]], "The fleet trial is not registered.")
    luna = list(csv.DictReader(io.StringIO(payload["data/config/LunaSettings.csv"].decode())))
    require(next(row for row in luna if row["fieldID"] == "omega3_mode")["defaultValue"] == "Observe",
            "The LunaLib default must also be Observe.")
    for name in ["graphics/omega_ai_icon.png", "data/missions/omega_ai3_trial/icon.png"]:
        png = payload[name]
        require(png[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}")
        width, height, depth, color_type = struct.unpack(">IIBB", png[16:26])
        require(width == height and 64 <= width <= 2048 and depth == 8 and color_type == 6,
                f"The icon must be a square RGBA PNG: {name}")
    require(payload["graphics/omega_ai_icon.png"] == payload["data/missions/omega_ai3_trial/icon.png"], "The mission icon is out of date.")
    with zipfile.ZipFile(io.BytesIO(payload["jars/OmegaAI.jar"])) as jar:
        require(jar.testzip() is None, "The JAR is damaged.")
        manifest = read_manifest(jar.read("META-INF/MANIFEST.MF"))
        require(manifest.get("Implementation-Version") == version, "The JAR version is out of date.")
        require(manifest.get("Starsector-Version") == mod["gameVersion"], "The JAR game version is out of date.")
        require(manifest.get("Omega-Source-SHA256") == source_digest(), "The JAR does not match the current source. Run the build.")
        classes = {n: jar.read(n) for n in jar.namelist() if n.endswith(".class")}
        expected_classes = {p.relative_to(ROOT / "build/classes/java/main").as_posix(): p.read_bytes()
                            for p in (ROOT / "build/classes/java/main").rglob("*.class")}
        require(bool(classes) and classes == expected_classes, "The JAR classes do not match the current build.")
        for mission_id in missions:
            require(f"data/missions/{mission_id}/MissionDefinition.class" in classes, f"The mission class is missing: {mission_id}")
        for name, data in classes.items():
            require(name.startswith(("omegaai3/", "data/missions/omega_ai3_trial/")), f"Unexpected class in JAR: {name}")
            require(struct.unpack(">H", data[6:8])[0] == 61, f"The class does not target Java 17: {name}")
            require(all(ref not in data for ref in [b"java/io/", b"java/nio/file/", b"java/lang/reflect/"]),
                    f"The class contains a blocked runtime API: {name}")
    return version


def inspect_tests() -> int:
    reports = list((ROOT / "build/test-results/test").glob("TEST-*.xml"))
    require(bool(reports), "No test report exists. Run gradlew clean build.")
    # Gradle can reuse tests after whitespace edits that produce identical classes.
    # Match the tested bytes, not source or report modification times.
    digest = hashlib.sha256()
    for path in sorted((ROOT / "build/classes/java").rglob("*.class"), key=lambda p: p.relative_to(ROOT).as_posix()):
        digest.update(path.relative_to(ROOT).as_posix().encode() + b"\0" + path.read_bytes() + b"\0")
    receipt = json.loads((ROOT / "build/verification/tests.json").read_bytes())
    require(receipt["classSha256"] == digest.hexdigest(), "The compiled classes differ from the tested classes. Run the build.")
    count = 0
    for path in reports:
        suite = ET.parse(path).getroot()
        require(all(int(suite.attrib.get(key, "0")) == 0 for key in ["failures", "errors", "skipped"]),
                f"The test report contains failures or skipped tests: {path.name}")
        count += int(suite.attrib["tests"])
    require(count > 0, "The test report contains no tests.")
    return count


def inspect_archive(path: Path, expected: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "The release ZIP is damaged.")
        require(len(archive.namelist()) == len(set(archive.namelist())), "The ZIP contains duplicate entries.")
        require(set(archive.namelist()) == {PREFIX + n for n in FILES}, "The ZIP layout is incorrect.")
        packaged = {n: archive.read(PREFIX + n) for n in FILES}
        inspect_payload(packaged)
        require(packaged == expected, "The ZIP content differs from the tested local files.")


def prepare_stage(root: Path, payload: dict[str, bytes]) -> Path:
    root = root.resolve()
    stage = root / PREFIX.rstrip("/")
    require(stage.resolve() == stage and stage.parent == root, "The staging folder must stay inside the mod project root.")
    allowed_dirs = {parent.as_posix() for name in payload for parent in Path(name).parents if parent.as_posix() != "."}
    if stage.exists():
        for path in stage.rglob("*"):
            require(path.resolve() == path, "The staging folder contains a link. Remove the link before packaging.")
            name = path.relative_to(stage).as_posix()
            require(name in allowed_dirs if path.is_dir() else name in payload,
                    f"Unexpected staging content: {name}. Remove it from the generated folder before packaging.")
    for name, content in payload.items():
        destination = stage / name
        require(destination.resolve().is_relative_to(stage), "A staging path escapes the mod folder.")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
    staged = {path.relative_to(stage).as_posix(): path.read_bytes() for path in stage.rglob("*") if path.is_file()}
    require(staged == payload, "The staging folder differs from the selected runtime files.")
    return stage


def write_archive(output, stage: Path) -> None:
    # Archive the physical staging folder, with its name at the archive root.
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(stage.rglob("*")):
            if not path.is_file():
                continue
            entry = zipfile.ZipInfo(path.relative_to(stage.parent).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, path.read_bytes())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", type=Path, help="Inspect an existing or downloaded ZIP without replacing it.")
    args = parser.parse_args()
    payload = {name: (ROOT / name).read_bytes() for name in FILES}
    version = inspect_payload(payload)
    tests = inspect_tests()
    output = args.verify or ROOT / f"Omega-AI-{version}.zip"
    if not args.verify:
        stage = prepare_stage(ROOT, payload)
        write_archive(output, stage)
        print(f"Staged folder: {stage}")
    inspect_archive(output, payload)
    print(f"Version: {version}; tests: {tests}; files: {len(payload)}")
    print(f"ZIP: {output.resolve()}")
    print(f"SHA-256: {hashlib.sha256(output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, StopIteration, OSError, zipfile.BadZipFile) as error:
        sys.exit(f"Packaging stopped: {error}")
