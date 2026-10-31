import unittest
from cleanup_test_threads import identified
from codex_fixture import require_fixture


class FixtureSafetyTest(unittest.TestCase):
    def test_tmp_alone_never_identifies_a_test(self):
        row = dict(cwd='/tmp', model_provider='openai', archived=1, first_user_message='', tokens_used=0)
        self.assertFalse(identified(row))
        row['cwd'] = '/tmp/my-real-project'
        self.assertFalse(identified(row))

    def test_empty_probe_requires_empty_archived_record(self):
        row = dict(cwd='/tmp/remote-gpt-create-check-abc', model_provider='openai', archived=1,
                   first_user_message='', tokens_used=0)
        self.assertTrue(identified(row))
        self.assertFalse(identified(dict(row, archived=0)))
        self.assertFalse(identified(dict(row, first_user_message='Real work')))
        self.assertFalse(identified(dict(row, tokens_used=1)))

    def test_provider_must_be_exact_and_location_must_be_temporary(self):
        row = dict(cwd='/tmp/remote-gpt-ssh-abc', model_provider='remote_gpt_status_fixture', archived=1,
                   first_user_message='Return the local status fixture response.', tokens_used=1)
        self.assertTrue(identified(row))
        self.assertFalse(identified(dict(row, cwd='/home/user/project')))
        self.assertFalse(identified(dict(row, model_provider='remote_gpt_custom')))

    def test_mutating_probe_rejects_the_normal_cli(self):
        with self.assertRaises(RuntimeError):
            require_fixture('codex')


if __name__ == '__main__':
    unittest.main()
