"""Package the documented release; excludes dependencies, test worlds and local logs."""
from pathlib import Path
import hashlib
import shutil
import zipfile
import re
root=Path(__file__).resolve().parents[1]
version=re.search(r"version = '([^']+)'",(root/'build.gradle').read_text()).group(1)
dest=root/'dist'/('Handily-'+version)
dest.mkdir(parents=True,exist_ok=True)
name='handily-neoforge-1.21.1-'+version
for suffix in ('.jar','-sources.jar'):
    shutil.copy2(root/'build/libs'/(name+suffix),dest/(name+suffix))
for file in ('README.md','TESTING.md','CHANGELOG.md','LICENSE','LICENSE_HANDSON_DELIGHT','LICENSE_HANDSON_VANILLA','THIRD_PARTY_NOTICES.md'):
    shutil.copy2(root/file,dest/file)
if (root/'docs/project-description.md').exists():
    shutil.copy2(root/'docs/project-description.md',dest/'project-description.md')
files=[]
for directory in ('src','tools','gradle','docs'):
    files.extend(p for p in (root/directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
for file in ('.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','dependencies.lock.json','README.md','TESTING.md','CHANGELOG.md','LICENSE','LICENSE_HANDSON_DELIGHT','LICENSE_HANDSON_VANILLA','THIRD_PARTY_NOTICES.md'):
    files.append(root/file)
with zipfile.ZipFile(dest/('Handily-'+version+'-project.zip'),'w',zipfile.ZIP_DEFLATED) as archive:
    for p in sorted(files):archive.write(p,Path('handily')/p.relative_to(root))
checks=[]
for p in sorted(dest.iterdir()):
    if p.is_file() and p.name!='SHA256SUMS.txt':checks.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name)
(dest/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\n')
print(dest)
