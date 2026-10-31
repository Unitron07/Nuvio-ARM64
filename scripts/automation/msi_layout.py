"""WiX container for an existing, audited jpackage image (no runtime rebuild)."""
import argparse
import hashlib
from pathlib import Path
import re
import uuid
import xml.etree.ElementTree as E

NS = 'http://wixtoolset.org/schemas/v4/wxs'
E.register_namespace('', NS)
UPGRADE = 'febc83be-f241-4a78-8f6c-10af5e358acc'


def node(parent, tag, **attributes):
    return E.SubElement(parent, '{' + NS + '}' + tag, attributes)


def identifier(prefix, text):
    return prefix + hashlib.sha256(text.encode()).hexdigest()[:30]


def generate(image, destination, version, name='Nuvio-ARM64', upgrade=UPGRADE):
    image = Path(image).resolve()
    if not re.fullmatch(r'1\.0\.[1-9][0-9]{0,4}', version) or int(version.split('.')[-1]) > 65535:
        raise ValueError('Invalid MSI sequence')
    if not (image / (name + '.exe')).is_file():
        raise ValueError('Missing native application launcher')
    files = sorted(p for p in image.rglob('*') if p.is_file())
    if any(p.is_symlink() or getattr(p, 'is_junction', lambda: False)() or not p.resolve().is_relative_to(image) for p in image.rglob('*')):
        raise ValueError('Symlinks / paths outside the audited image are forbidden')
    root = E.Element('{' + NS + '}Wix')
    package = node(root, 'Package', Name=name, Manufacturer='Nuvio-ARM64 contributors',
                   Version=version, UpgradeCode=upgrade, Scope='perUser', InstallerVersion='500', Language='1033')
    node(package, 'MajorUpgrade', DowngradeErrorMessage='A newer fork version is already installed.',
         Schedule='afterInstallInitialize')
    node(package, 'MediaTemplate', EmbedCab='yes', CompressionLevel='high')
    node(package, 'Property', Id='ARPNOMODIFY', Value='1')
    node(package, 'Property', Id='ARPURLINFOABOUT', Value='https://github.com/Unitron07/Nuvio-ARM64')
    local = node(package, 'StandardDirectory', Id='LocalAppDataFolder')
    programs = node(local, 'Directory', Id='LocalPrograms', Name='Programs')
    install = node(programs, 'Directory', Id='INSTALLFOLDER', Name=name)
    directories = {'.': install}
    feature = node(package, 'Feature', Id='Complete', Title=name, Level='1')
    seen = set()
    cleaned_directories = set()
    for path in files:
        relative = path.relative_to(image)
        parent = install
        partial = Path('.')
        for segment in relative.parts[:-1]:
            partial /= segment
            key = partial.as_posix()
            if key not in directories:
                directories[key] = node(parent, 'Directory', Id=identifier('D', key), Name=segment)
            parent = directories[key]
        key = relative.as_posix().lower()
        if key in seen:
            raise ValueError('Case-colliding Windows payload paths')
        seen.add(key)
        cid = identifier('C', key)
        component = node(parent, 'Component', Id=cid, Guid=str(uuid.uuid5(uuid.UUID(upgrade), key)), Bitness='always64')
        node(component, 'File', Id=identifier('F', key), Source=str(path))
        node(component, 'RegistryValue', Root='HKCU', Key=f'Software\\{name}\\Installer\\Files',
             Name=cid, Type='integer', Value='1', KeyPath='yes')
        directory_key = relative.parent.as_posix()
        if directory_key not in cleaned_directories:
            node(component, 'RemoveFolder', Id=identifier('R', directory_key), On='uninstall')
            cleaned_directories.add(directory_key)
        node(feature, 'ComponentRef', Id=cid)
    desktop = node(package, 'StandardDirectory', Id='DesktopFolder')
    menu_root = node(package, 'StandardDirectory', Id='ProgramMenuFolder')
    menu = node(menu_root, 'Directory', Id='ForkMenu', Name=name)
    for parent, cid in [(desktop, 'DesktopShortcut'), (menu, 'MenuShortcut')]:
        component = node(parent, 'Component', Id=cid, Guid=str(uuid.uuid5(uuid.UUID(upgrade), cid)), Bitness='always64')
        node(component, 'Shortcut', Id=cid + 'Link', Name=name, Target=f'[INSTALLFOLDER]{name}.exe',
             WorkingDirectory='INSTALLFOLDER')
        node(component, 'RegistryValue', Root='HKCU', Key=f'Software\\{name}\\Installer',
             Name=cid, Type='integer', Value='1', KeyPath='yes')
        if cid == 'MenuShortcut':
            node(component, 'RemoveFolder', Id='RemoveForkMenu', On='uninstall')
        node(feature, 'ComponentRef', Id=cid)
    E.indent(root)
    E.ElementTree(root).write(destination, encoding='utf-8', xml_declaration=True)
    return len(files)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--image', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--version', required=True)
    p.add_argument('--name', default='Nuvio-ARM64')
    p.add_argument('--upgrade', default=UPGRADE)
    a = p.parse_args()
    print(f'Files authored: {generate(a.image, a.output, a.version, a.name, a.upgrade)}')
