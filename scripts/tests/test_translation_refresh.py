import importlib.util
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

path = Path(__file__).resolve().parents[1] / 'refresh-upstream-translations.py'
spec = importlib.util.spec_from_file_location('translations', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def entry(text):
    return ET.fromstring(text)


class TranslationContractsTest(unittest.TestCase):
    def test_reordered_positional_arguments_are_compatible(self):
        english = entry('<string name="x">%1$s has %2$d connections</string>')
        translated = entry('<string name="x">%2$d connexions pour %1$s</string>')
        self.assertTrue(module.compatible(english, translated, set()))
        self.assertFalse(module.compatible(english, entry('<string name="x">%1$d %2$s</string>'), set()))

    def test_brand_and_unknown_references_are_not_imported(self):
        english = entry('<string name="x">Blocked</string>')
        self.assertFalse(module.compatible(english, entry('<string name="x">Rethink blocked</string>'), set()))
        self.assertFalse(module.compatible(english, entry('<string name="x">@string/obsolete</string>'), set()))

    def test_string_and_array_with_same_name_remain_distinct(self):
        text = '<resources><string-array name="rules"><item>One</item></string-array><string name="rules">Rules</string></resources>'
        values = module.entries(text)
        self.assertEqual(2, len(values))
        updated = module.replace_entries(text, {('string-array', 'rules'): entry('<string-array name="rules"><item>Uno</item></string-array>')})
        self.assertEqual('Rules', module.entries(updated)[('string', 'rules')].text)
        self.assertEqual('Uno', module.entries(updated)[('string-array', 'rules')][0].text)

    def test_formatting_contract_is_part_of_english_matching(self):
        first = entry('<string name="x" formatted="false">%s</string>')
        second = entry('<string name="x">%s</string>')
        self.assertNotEqual(module.signature(first), module.signature(second))

    def test_locale_plural_quantities_validate_against_english_other(self):
        english = entry('<plurals name="x"><item quantity="one">%d app</item><item quantity="other">%d apps</item></plurals>')
        translated = entry('<plurals name="x"><item quantity="few">%d applications</item><item quantity="other">%d applications</item></plurals>')
        self.assertTrue(module.compatible(english, translated, set()))

    def test_existing_comments_and_unrelated_strings_survive(self):
        text = '<resources><!-- keep --><string name="x">Old</string><string name="untouched">Keepout</string></resources>'
        updated = module.replace_entries(text, {('string', 'x'): entry('<string name="x">New</string>')})
        self.assertIn('<!-- keep -->', updated)
        self.assertIn('<string name="untouched">Keepout</string>', updated)


if __name__ == '__main__':
    unittest.main()
