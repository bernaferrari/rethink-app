import base64
import hashlib
import importlib.util
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / 'build-signed-release.py'
spec = importlib.util.spec_from_file_location('release_signing', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseSigningTest(unittest.TestCase):
    certificate = b'fixture-certificate'

    def environment(self):
        return {
            'RELEASE_CHANNEL': 'fdroid', 'VERSION_CODE': '1',
            'EXPECTED_CERT_SHA256': hashlib.sha256(self.certificate).hexdigest(),
            'RELEASE_KEYSTORE_BASE64': base64.b64encode(b'fixture-keystore').decode(),
            'RELEASE_KEY_ALIAS': 'fixture', 'RELEASE_STORE_PASSWORD': 'fixture-only',
            'RELEASE_KEY_PASSWORD': 'fixture-only', 'SAFE_SETTING': 'preserved',
        }

    def test_invalid_versions_cannot_reach_keytool_or_gradle(self):
        for value in ('0', '-1', '10000000', '0001', '1; invalid'):
            environment = self.environment() | {'VERSION_CODE': value}
            with self.subTest(version=value), patch.dict(os.environ, environment, clear=True), \
                    patch.object(module.subprocess, 'run') as command:
                with self.assertRaisesRegex(ValueError, 'VERSION_CODE'):
                    module.main()
                command.assert_not_called()

    def test_wrong_certificate_prevents_build_and_removes_temporary_key(self):
        environment = self.environment() | {'EXPECTED_CERT_SHA256': '0' * 64}
        with patch.dict(os.environ, environment, clear=True), \
                patch.object(module.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout=self.certificate)) as command, \
                patch.object(module, 'build_and_sign') as build:
            with self.assertRaisesRegex(ValueError, 'certificate does not match'):
                module.main()
            build.assert_not_called()
            args = command.call_args.args[0]
            self.assertFalse(Path(args[args.index('-keystore') + 1]).exists())

    def test_failed_build_receives_no_signing_secrets_and_always_removes_key(self):
        key_paths = []

        def failed_build(channel, version, certificate, workspace, keystore, environment):
            self.assertEqual(('fdroid', '1'), (channel, version))
            self.assertTrue(module.SECRETS.isdisjoint(environment))
            self.assertEqual('preserved', environment['SAFE_SETTING'])
            self.assertEqual(0o600, keystore.stat().st_mode & 0o777)
            self.assertEqual(b'fixture-keystore', keystore.read_bytes())
            key_paths.append(keystore)
            raise ValueError('fixture build failed')

        environment = self.environment()
        encoded = environment['RELEASE_KEYSTORE_BASE64']
        environment['RELEASE_KEYSTORE_BASE64'] = ' ' + encoded[:4] + '\n' + encoded[4:] + ' '
        with patch.dict(os.environ, environment, clear=True), \
                patch.object(module.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout=self.certificate)), \
                patch.object(module, 'build_and_sign', side_effect=failed_build):
            with self.assertRaisesRegex(ValueError, 'fixture build failed'):
                module.main()
        self.assertEqual(1, len(key_paths))
        self.assertFalse(key_paths[0].parent.exists())
