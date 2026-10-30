import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / 'verify-release-apks.py'
spec = importlib.util.spec_from_file_location('release_apks', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseManifestTest(unittest.TestCase):
    badging = "package: name='com.bernaferrari.bravedns' versionCode='30000001' versionName='test'"
    tree = '''E: manifest
  E: application
    E: activity-alias
      A: android:name="LauncherAliasAppLock"
    E: service
      A: android:name="com.bernaferrari.bravedns.service.BraveVPNService"
      A: android:permission="android.permission.BIND_VPN_SERVICE"
'''

    def test_correct_release_identity_and_protected_vpn_pass(self):
        module.check_manifest(self.badging, self.tree, 30000001)

    def test_debuggable_and_test_only_releases_are_rejected(self):
        with self.assertRaises(ValueError):
            module.check_manifest(self.badging + '\napplication-debuggable', self.tree, 30000001)
        with self.assertRaises(ValueError):
            module.check_manifest(self.badging, self.tree + '\n A: android:testOnly(0x123)=(type 0x12)0xffffffff', 30000001)

    def test_wrong_identity_version_or_unprotected_vpn_are_rejected(self):
        with self.assertRaises(ValueError):
            module.check_manifest(self.badging, self.tree, 90000001)
        with self.assertRaises(ValueError):
            module.check_manifest(self.badging.replace('com.bernaferrari.bravedns', 'com.other.app'), self.tree, 30000001)
        with self.assertRaises(ValueError):
            module.check_manifest(self.badging, self.tree.replace('android.permission.BIND_VPN_SERVICE', 'other.permission'), 30000001)


if __name__ == '__main__':
    unittest.main()
