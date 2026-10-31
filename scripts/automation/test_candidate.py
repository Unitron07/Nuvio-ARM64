import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import Mock, patch

import prepare_candidate as p


SHA = 'a' * 40
SOURCE = 'b' * 40
RECORD = {'release_id': 42, 'upstream_tag': '1.2.3-alpha', 'upstream_sha': SHA,
          'downstream_sha': SOURCE, 'revision': 1, 'package_sequence': 1, 'delivery_recipient': 'age1' + 'a' * 58}


class CandidateTests(unittest.TestCase):
    def prepare(self, record=RECORD, ledger=None, release=None, ancestor=True, resolved=SHA):
        api = Mock()
        api.request.return_value = release or {'tag_name': record['upstream_tag'], 'draft': False,
                                               'prerelease': False, 'html_url': 'https://example/release'}
        with patch.object(Path, 'read_text', return_value=json.dumps(ledger or {'test': record})), \
             patch.object(p, 'ancestry', return_value=ancestor), \
             patch.object(p, 'git', return_value=resolved):
            return p.prepare('test', api)

    def test_approved_integrated_source_and_preview_policy(self):
        result = self.prepare()
        self.assertTrue(result['prerelease'])
        self.assertEqual(result['version'], '1.2.3-alpha+arm64.1')
        self.assertEqual(result['package_version'], '1.0.1')
        self.assertEqual(result['downstream_sha'], SOURCE)

    def test_unintegrated_source(self):
        with self.assertRaisesRegex(ValueError, 'not integrated'):
            self.prepare(ancestor=False)

    def test_moved_upstream_tag(self):
        with self.assertRaisesRegex(ValueError, 'tag moved'):
            self.prepare(resolved=SOURCE)

    def test_missing_or_replaced_release(self):
        with self.assertRaisesRegex(ValueError, 'replaced'):
            self.prepare(release={'draft': False, 'tag_name': 'other'})
        with self.assertRaisesRegex(ValueError, 'replaced'):
            self.prepare(release={'draft': True})

    def test_duplicate_version_or_sequence(self):
        for other in [RECORD | {'revision': 2}, RECORD | {'package_sequence': 2}]:
            with self.assertRaisesRegex(ValueError, 'already allocated'):
                self.prepare(ledger={'test': RECORD, 'duplicate': other})

    def test_mutable_or_invalid_source(self):
        with self.assertRaisesRegex(ValueError, 'immutable'):
            self.prepare(record=RECORD | {'downstream_sha': 'main'})


class CheckoutMetadataTests(unittest.TestCase):
    def test_all_gitlinks_have_a_submodule_mapping(self):
        root = Path(__file__).resolve().parents[2]
        gitlinks = subprocess.check_output(['git', 'ls-files', '--stage'], cwd=root, text=True)
        mappings = subprocess.check_output(['git', 'config', '-f', '.gitmodules', '--get-regexp', r'^submodule\..*\.path$'],
                                          cwd=root, text=True)
        paths = [line.split(' ', 1)[1] for line in mappings.splitlines()]
        for line in gitlinks.splitlines():
            if line.startswith('160000 '):
                self.assertIn(line.split('\t')[1], paths)


if __name__ == '__main__':
    unittest.main()
