import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / 'prepare-debug-firebase.py'
spec = importlib.util.spec_from_file_location('debug_firebase', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DebugFirebaseFixtureTest(unittest.TestCase):
    def test_clean_checkout_gets_only_debug_options_with_valid_firebase_shapes(self):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            (root / 'app').mkdir()
            self.assertTrue(module.prepare_debug_fixture(root))
            target = root / 'app/src/debug/google-services.json'
            self.assertEqual([target], list(root.rglob('google-services.json')))
            fixture = json.loads(target.read_text())
            client = fixture['client'][0]
            self.assertEqual('com.bernaferrari.bravedns', client['client_info']['android_client_info']['package_name'])
            self.assertRegex(client['client_info']['mobilesdk_app_id'], r'^1:\d+:android:[a-f0-9]+$')
            self.assertRegex(client['api_key'][0]['current_key'], r'^A[\w-]{38}$')
            self.assertTrue(fixture['project_info']['project_id'].startswith('demo-'))
            self.assertIn('nonproduction', fixture['keepout_ci_fixture'])
            self.assertEqual('1', fixture['configuration_version'])
            self.assertFalse((root / 'app/src/release').exists())

    def test_provided_root_or_source_set_configs_are_preserved_without_shadowing(self):
        for location in ('app/google-services.json', 'app/src/debug/google-services.json',
                         'app/src/play/google-services.json', 'app/src/release/google-services.json'):
            with self.subTest(location=location), tempfile.TemporaryDirectory() as workspace:
                root = Path(workspace)
                target = root / location
                target.parent.mkdir(parents=True)
                target.write_bytes(b'provided configuration: do not modify')
                self.assertFalse(module.prepare_debug_fixture(root))
                self.assertEqual(b'provided configuration: do not modify', target.read_bytes())
                self.assertEqual([target], list(root.rglob('google-services.json')))

    def test_repeated_run_preserves_the_original_fixture_bytes(self):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            (root / 'app').mkdir()
            module.prepare_debug_fixture(root)
            target = root / 'app/src/debug/google-services.json'
            original = target.read_bytes()
            self.assertFalse(module.prepare_debug_fixture(root))
            self.assertEqual(original, target.read_bytes())

    def test_debug_directory_symlink_cannot_write_into_release(self):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            release = root / 'app/src/release'
            release.mkdir(parents=True)
            (root / 'app/src/debug').symlink_to(release, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'symlink'):
                module.prepare_debug_fixture(root)
            self.assertEqual([], list(root.rglob('google-services.json')))

    def test_a_configuration_created_concurrently_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as workspace:
            root = Path(workspace)
            (root / 'app').mkdir()
            original_open = Path.open

            def competing_write(path, mode='r', *args, **kwargs):
                if mode == 'x':
                    path.write_text('provided concurrently')
                return original_open(path, mode, *args, **kwargs)

            with patch.object(Path, 'open', competing_write):
                self.assertFalse(module.prepare_debug_fixture(root))
            self.assertEqual('provided concurrently', (root / 'app/src/debug/google-services.json').read_text())
