from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as E
from unittest.mock import Mock

from msi_layout import generate, NS, UPGRADE
from prepare_candidate import assert_first_attempt


class LayoutTests(unittest.TestCase):
    def test_complete_per_user_payload_and_fork_registration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); image=root/'image'; (image/'app').mkdir(parents=True)
            (image/'Nuvio-ARM64.exe').write_bytes(b'fixture')
            (image/'app'/'one & two.jar').write_bytes(b'data')
            self.assertEqual(generate(image,root/'layout.wxs','1.0.1'),2)
            package=E.parse(root/'layout.wxs').getroot().find('{'+NS+'}Package')
            self.assertEqual(package.get('Scope'),'perUser')
            self.assertEqual(package.get('UpgradeCode'),UPGRADE)
            self.assertEqual(len(package.findall('.//{'+NS+'}File')),2)
            registry=package.findall('.//{'+NS+'}RegistryValue')
            self.assertTrue(all(v.get('Root')=='HKCU' and v.get('Key').startswith('Software\\Nuvio-ARM64\\Installer') for v in registry))
            self.assertTrue(all(s.get('Target')=='[INSTALLFOLDER]Nuvio-ARM64.exe' for s in package.findall('.//{'+NS+'}Shortcut')))
            self.assertFalse(package.findall('.//{'+NS+'}CustomAction'))

    def test_component_guids_remain_stable_for_future_upgrades(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); image=root/'image';image.mkdir();(image/'Nuvio-ARM64.exe').write_bytes(b'v1')
            generate(image,root/'one.wxs','1.0.1')
            (image/'Nuvio-ARM64.exe').write_bytes(b'v2')
            generate(image,root/'two.wxs','1.0.2')
            def guids(path): return [c.get('Guid') for c in E.parse(path).getroot().iter('{'+NS+'}Component')]
            self.assertEqual(guids(root/'one.wxs'),guids(root/'two.wxs'))

    def test_invalid_msi_sequences_and_missing_launcher(self):
        with tempfile.TemporaryDirectory() as tmp:
            for version in ['1.0.0','1.0.65536','0.1.32-alpha','1.0.01']:
                with self.assertRaises(ValueError):generate(tmp,Path(tmp)/'x.wxs',version)
            with self.assertRaisesRegex(ValueError,'launcher'):generate(tmp,Path(tmp)/'x.wxs','1.0.1')


class AttemptTests(unittest.TestCase):
    def test_rerun_is_rejected_before_any_build(self):
        with self.assertRaisesRegex(ValueError,'reruns'):assert_first_attempt('alpha32-r1',Mock(),'10','2')

    def test_previous_dispatch_freezes_candidate_version(self):
        api=Mock()
        api.request.side_effect=[{'created_at':'2026-10-30T10:00:00Z'}, {'workflow_runs':[{'id':9,'created_at':'2026-10-30T09:00:00Z','display_title':'ARM64 candidate alpha32-r1'}]}]
        with self.assertRaisesRegex(ValueError,'already dispatched'):assert_first_attempt('alpha32-r1',api,'10','1')

    def test_first_dispatch_does_not_conflict_with_newer_queued_run(self):
        api=Mock()
        api.request.side_effect=[{'created_at':'2026-10-30T10:00:00Z'}, {'workflow_runs':[{'id':11,'created_at':'2026-10-30T10:00:00Z','display_title':'ARM64 candidate alpha32-r1'}]}]
        assert_first_attempt('alpha32-r1',api,'10','1')


if __name__=='__main__':unittest.main()
