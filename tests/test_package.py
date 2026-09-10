"""Resource and extraction regressions. Run after the Java build."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("omega_package", ROOT / "package.py")
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class PackagingTests(unittest.TestCase):
    def setUp(self):
        self.payload = {name: (ROOT / name).read_bytes() for name in package.FILES}

    def test_every_registered_mission_has_its_startup_resources(self):
        self.assertEqual(["omega_ai3_trial"], package.inspect_resources(self.payload))
        for missing in ["mission_text.txt", "descriptor.json", "icon.png"]:
            with self.subTest(missing=missing):
                payload = dict(self.payload)
                del payload["data/missions/omega_ai3_trial/" + missing]
                with self.assertRaisesRegex(ValueError, "Missing or empty runtime resource"):
                    package.inspect_resources(payload)

    def test_empty_mission_text_is_rejected(self):
        self.payload["data/missions/omega_ai3_trial/mission_text.txt"] = b" \n"
        with self.assertRaisesRegex(ValueError, "mission_text.txt"):
            package.inspect_resources(self.payload)

    def test_luna_icon_registration_needs_the_correct_mod_id_and_existing_image(self):
        for config in [{}, {"wrong_id": {"iconPath": "graphics/omega_ai_icon.png"}},
                       {"omega_ai3": {"iconPath": "graphics/missing.png"}}]:
            with self.subTest(config=config):
                self.payload["data/config/LunaSettingsConfig.json"] = json.dumps(config).encode()
                with self.assertRaises(ValueError):
                    package.inspect_resources(self.payload)

    def test_version_tracker_and_built_jar_match(self):
        self.assertEqual(json.loads(self.payload["mod_info.json"])["version"], package.inspect_payload(self.payload))

    def test_forum_topic_is_optional_until_published_then_must_match(self):
        package.inspect_forum_metadata({}, {})
        package.inspect_forum_metadata({"modThreadId": "123"}, {"modThreadId": 123})
        for mod, version in [({"modThreadId": "123"}, {}), ({}, {"modThreadId": 123}),
                             ({"modThreadId": 123}, {"modThreadId": 123}),
                             ({"modThreadId": "123"}, {"modThreadId": "123"}),
                             ({"modThreadId": "123"}, {"modThreadId": 456}),
                             ({"modThreadId": "0"}, {"modThreadId": 0})]:
            with self.subTest(mod=mod, version=version), self.assertRaises(ValueError):
                package.inspect_forum_metadata(mod, version)

    def test_staging_and_zip_extract_as_one_runtime_only_mod_folder(self):
        # In-memory ZIPs avoid leaving generated test archives outside the mod root.
        with tempfile.TemporaryDirectory(prefix="omega-package-") as temporary:
            root = Path(temporary).resolve()
            stage = package.prepare_stage(root, self.payload)
            self.assertEqual(root / "OmegaAI", stage)
            first, second = io.BytesIO(), io.BytesIO()
            package.write_archive(first, stage)
            package.write_archive(second, stage)
            self.assertEqual(first.getvalue(), second.getvalue())
            with zipfile.ZipFile(first) as archive:
                expected_names = {"OmegaAI/" + name for name in self.payload}
                self.assertEqual(expected_names, set(archive.namelist()))
                self.assertFalse(any(name.endswith((".java", ".py", ".md")) for name in archive.namelist()))
                extracted = root / "mods"
                archive.extractall(extracted)
                installed = extracted / "OmegaAI"
                actual = {p.relative_to(installed).as_posix(): p.read_bytes() for p in installed.rglob("*") if p.is_file()}
                self.assertEqual(self.payload, actual)
                self.assertTrue((installed / "mod_info.json").is_file())
                package.inspect_resources(actual)

    def test_staging_refuses_unexpected_source_instead_of_deleting_it(self):
        with tempfile.TemporaryDirectory(prefix="omega-package-") as temporary:
            root = Path(temporary).resolve()
            stage = package.prepare_stage(root, self.payload)
            unexpected = stage / "src" / "Unrelated.java"
            unexpected.parent.mkdir()
            unexpected.write_text("preserve")
            with self.assertRaisesRegex(ValueError, "Unexpected staging content"):
                package.prepare_stage(root, self.payload)
            self.assertEqual("preserve", unexpected.read_text())


if __name__ == "__main__":
    unittest.main()
