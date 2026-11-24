"""Check the TV/phone debug packages without installing them or using signing secrets."""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LIBVLC = ('libvlc.so', 'libvlcjni.so', 'libc++_shared.so')
TV_ABIS = ('armeabi-v7a', 'arm64-v8a')
PHONE_ABIS = ('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')
# ELF class (1 = 32-bit, 2 = 64-bit) and e_machine for each Android ABI.
ELF_ABI = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86': (1, 3), 'x86_64': (2, 62)}
PAGE_16K = 16384


def native_set(abis):
    return {f'lib/{abi}/{library}' for abi in abis for library in LIBVLC}


def check_elf(name, data, abi):
    """Phone libraries match their ABI directory; 64-bit LOAD segments support 16 KiB pages."""
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError(f'{name}: not a little-endian ELF library')
    if (data[4], struct.unpack_from('<H', data, 18)[0]) != ELF_ABI[abi]:
        raise ValueError(f'{name}: ELF class or machine does not match {abi}')
    if data[4] != 2:
        return
    program_headers = struct.unpack_from('<Q', data, 32)[0]
    entry_size, count = struct.unpack_from('<HH', data, 54)
    loads = 0
    for index in range(count):
        offset = program_headers + index * entry_size
        if struct.unpack_from('<I', data, offset)[0] != 1:  # PT_LOAD
            continue
        loads += 1
        if struct.unpack_from('<Q', data, offset + 48)[0] < PAGE_16K:
            raise ValueError(f'{name}: LOAD segment is not 16 KiB aligned')
    if not loads:
        raise ValueError(f'{name}: no LOAD segments')


def check_zip_alignment(apk, info):
    """A 64-bit phone library is stored uncompressed at a 16 KiB-aligned offset, so it maps in place."""
    if info.compress_type != zipfile.ZIP_STORED:
        raise ValueError(f'{info.filename}: native library is compressed')
    with open(apk, 'rb') as handle:
        handle.seek(info.header_offset)
        header = handle.read(30)
    if header[:4] != b'PK\x03\x04':
        raise ValueError(f'{info.filename}: unexpected local ZIP header')
    name_length, extra_length = struct.unpack_from('<HH', header, 26)
    if (info.header_offset + 30 + name_length + extra_length) % PAGE_16K:
        raise ValueError(f'{info.filename}: not 16 KiB zip-aligned')


def verify(aapt, apk, package, minimum, target, version, code, native, phone=False):
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
        if phone:
            for name in sorted(libraries):
                abi = name.split('/')[1]
                check_elf(name, archive.read(name), abi)
                if ELF_ABI[abi][0] == 2:
                    check_zip_alignment(apk, archive.getinfo(name))
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
    for module, package, minimum, target, libraries, phone in (
            ('app', 'com.fieldview.tv', 22, 35, native_set(TV_ABIS), False),
            ('mobile', 'com.fieldview.mobile', 26, 36, native_set(PHONE_ABIS), True)):
        output = args.build_root / module if args.build_root else ROOT / module / 'build'
        apk = output / 'outputs' / 'apk' / 'debug' / f'{module}-debug.apk'
        expected_version = version + '-surface-trial' if module == 'app' and args.native_surface_trial else version
        print(json.dumps(verify(args.aapt.resolve(), apk, package, minimum, target,
                                expected_version, code, libraries, phone)))


if __name__ == '__main__':
    main()
