"""Check the TV/phone debug packages without installing them or using signing secrets."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(aapt, apk, package, minimum, target, version, code, native):
    badging = subprocess.run([str(aapt), 'dump', 'badging', str(apk)],
                             check=True, capture_output=True, text=True).stdout
    expected = {
        'package': (r"package: name='([^']+)'", package),
        'version': (r"versionName='([^']+)'", version),
        'code': (r"versionCode='([^']+)'", str(code)),
        'min_sdk': (r"^sdkVersion:'([^']+)'", str(minimum)),
        'target_sdk': (r"^targetSdkVersion:'([^']+)'", str(target)),
    }
    for field, (pattern, value) in expected.items():
        found = re.search(pattern, badging, re.MULTILINE)
        if not found or found[1] != value:
            raise ValueError(f'{apk.name}: unexpected {field}')
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'{apk.name}: corrupt ZIP entry')
        libraries = {name for name in archive.namelist() if name.startswith('lib/') and not name.endswith('/')}
    if libraries != native:
        raise ValueError(f'{apk.name}: unexpected native-library packaging: {sorted(libraries)}')
    return dict(package=package, version=version, code=code, min_sdk=minimum,
                target_sdk=target, native_libraries=len(libraries), bytes=apk.stat().st_size)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aapt', type=Path, required=True)
    parser.add_argument('--build-root', type=Path, help='External propertyTrackerBuildRoot, if used')
    parser.add_argument('--native-surface-trial', action='store_true',
                        help='Expect the opt-in TV debug trial suffix; phone identity stays unchanged')
    args = parser.parse_args()
    config = (ROOT / 'build.gradle').read_text(encoding='utf-8')
    version = re.search(r"propertyTrackerVersion = '([^']+)'", config)[1]
    code = int(re.search(r'propertyTrackerVersionCode = (\d+)', config)[1])
    native = {f'lib/{abi}/{library}' for abi in ('armeabi-v7a', 'arm64-v8a')
              for library in ('libvlc.so', 'libvlcjni.so', 'libc++_shared.so')}
    for module, package, minimum, target, libraries in (
            ('app', 'com.fieldview.tv', 22, 35, native),
            ('mobile', 'com.fieldview.mobile', 26, 36, set())):
        output = args.build_root / module if args.build_root else ROOT / module / 'build'
        apk = output / 'outputs' / 'apk' / 'debug' / f'{module}-debug.apk'
        expected_version = version + '-surface-trial' if module == 'app' and args.native_surface_trial else version
        print(json.dumps(verify(args.aapt.resolve(), apk, package, minimum, target,
                                expected_version, code, libraries)))


if __name__ == '__main__':
    main()
