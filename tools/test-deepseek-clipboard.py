"""One explicit live check; receive the secret on stdin, never save or print it."""
import json
import pathlib
import re
import sys
import time
import urllib.request
import urllib.error

root = pathlib.Path(__file__).resolve().parents[1]
secret = sys.stdin.read().strip().lstrip('\ufeff')
if not re.fullmatch(r'sk-[A-Za-z0-9_-]{16,}', secret):
    raise SystemExit('No recognizable API key received.')
fixture = json.loads((root / 'app/src/test/resources/edge-anonymized-extraction.json').read_text(encoding='utf-8-sig'))
early = next(row for row in fixture['rows'] if row['timing'].startswith('(14'))
rows = [fixture['rows'][0], fixture['rows'][8], early]
source = {'detected': True, 'scope': 'full', 'term': fixture['term'], 'rows': rows}
body = {
    'model': 'deepseek-flash', 'thinking': {'type': 'disabled'},
    'response_format': {'type': 'json_object'}, 'temperature': 0, 'max_tokens': 16384,
    'messages': [
        {'role': 'system', 'content': (root / 'app/src/main/assets/deepseek-import-prompt.txt').read_text(encoding='utf-8')},
        {'role': 'user', 'content': json.dumps(source, ensure_ascii=False)},
    ],
}
request = urllib.request.Request('https://api.deepseek.com/chat/completions',
    data=json.dumps(body, ensure_ascii=False).encode('utf-8'),
    headers={'Authorization': 'Bearer ' + secret, 'Content-Type': 'application/json'})
started = time.monotonic()
try:
    with urllib.request.urlopen(request, timeout=95) as response:
        result = json.load(response)
except urllib.error.HTTPError as error:
    raise SystemExit('DeepSeek live check failed with HTTP ' + str(error.code)) from None
except Exception as error:
    raise SystemExit('DeepSeek live check failed: ' + type(error).__name__) from None
finally:
    secret = None
choice = result['choices'][0]
assert choice['finish_reason'] == 'stop', 'Incomplete response'
answer = json.loads(choice['message']['content'])
assert answer['complete'] is True
assert len(answer['rows']) == len(rows)
def weeks(text):
    text = text.replace('（', '(').replace('）', ')').replace('，', ',')
    found = set()
    for piece in text.split(','):
        match = re.fullmatch(r'(\d+)(?:-(\d+))?周?(?:\(([单双])(?:周)?\))?', piece)
        assert match, 'Unrecognized week expression'
        first, last = int(match[1]), int(match[2] or match[1])
        found.update(n for n in range(first, last + 1) if not match[3] or n % 2 == (1 if match[3] == '单' else 0))
    return found
def timing(text):
    match = re.fullmatch(r'\((\d+)(?:-(\d+))?节\)(.+)', text.replace('（', '(').replace('）', ')'))
    assert match, 'Unrecognized timing'
    return int(match[1]), int(match[2] or match[1]), weeks(match[3])
expected = {row['name']: row for row in rows}
for row in answer['rows']:
    original = expected[row['name']]
    assert row['day'] == original['day']
    assert timing(row['timing']) == timing(original['timing']), 'Timing differs from anonymous source'
(root / 'verification/deepseek-live-response.json').write_text(json.dumps(answer, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: real DeepSeek API recognized 3 anonymous courses, including mixed parity and early reading.')
print('Model: ' + result['model'])
print('Duration seconds: ' + str(round(time.monotonic() - started, 2)))
print('Token usage: ' + json.dumps(result.get('usage', {})))
