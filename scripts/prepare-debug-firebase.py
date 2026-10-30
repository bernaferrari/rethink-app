#!/usr/bin/env python3
"""Prepare synthetic, nonproduction Firebase options for debug CI builds only.

Never copies credentials or overwrites a supplied configuration. The debug source set
keeps this fixture out of release builds; Play/website releases require their own config.
"""
import json
from pathlib import Path


def prepare_debug_fixture(root: Path) -> bool:
    app = root / 'app'
    target = app / 'src' / 'debug' / 'google-services.json'
    if not app.is_dir():
        raise ValueError('Expected an Android app directory')
    for directory in (app, app / 'src', target.parent):
        if directory.is_symlink():
            raise ValueError('Refusing to write a debug fixture through a symlink')
    supplied = [app / 'google-services.json', *list((app / 'src').rglob('google-services.json'))]
    if any(path.exists() or path.is_symlink() for path in supplied):
        return False
    configuration = {
        'keepout_ci_fixture': 'Synthetic nonproduction debug CI only; never deploy these options.',
        'project_info': {'project_number': '000000000000', 'project_id': 'demo-keepout-debug-ci'},
        'client': [{
            'client_info': {
                'mobilesdk_app_id': '1:000000000000:android:00000000000000000000000000000000',
                'android_client_info': {'package_name': 'com.bernaferrari.bravedns'},
            },
            'api_key': [{'current_key': 'AIza' + '0' * 35}],
        }],
        'configuration_version': '1',
    }
    target.parent.mkdir(parents=True, exist_ok=True)
    try:
        with target.open('x', encoding='utf-8') as output:
            json.dump(configuration, output, indent=2)
            output.write('\n')
    except FileExistsError:
        return False
    return True


if __name__ == '__main__':
    created = prepare_debug_fixture(Path(__file__).resolve().parents[1])
    print('Created synthetic debug-only Google Services fixture.' if created else
          'Preserved existing Google Services configuration; no fixture written.')
