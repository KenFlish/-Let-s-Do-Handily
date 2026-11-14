"""Download locked external dependencies. Does not modify any Minecraft installation."""
import hashlib
import json
import urllib.request
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
libs = root / 'libs'
libs.mkdir(exist_ok=True)
for entry in json.loads((root / 'dependencies.lock.json').read_text(encoding='utf-8')):
    dest = libs / entry.get('directory', '') / entry['filename']
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest() == entry['sha256']:
        print('Verified', dest.name)
        continue
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'Handily/0.5.0 dependency-bootstrap'})
    with urllib.request.urlopen(request, timeout=120) as response:
        content = response.read()
    if hashlib.sha256(content).hexdigest() != entry['sha256']:
        raise RuntimeError('SHA256 mismatch: ' + entry['filename'])
    dest.write_bytes(content)
    print('Downloaded and verified', dest.name)
# Foundation is already distributed inside Farm & Charm, not an extra player install.
with zipfile.ZipFile(libs / 'letsdo-farm_and_charm-neoforge-1.1.29.jar') as archive:
    content = archive.read('META-INF/jars/foundation-neoforge-1.0.7.jar')
    if hashlib.sha256(content).hexdigest() != '4251e639c1bfc537e5538df792de082586f91fbb7b5d258dcd4258d3b5092b42':
        raise RuntimeError('Unexpected bundled Foundation version')
    (libs / 'foundation-neoforge-1.0.7.jar').write_bytes(content)
print('Ready. Build with Java 21: gradlew.bat build')
