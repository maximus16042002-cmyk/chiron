"""The owner's phone database, as a P5 export - DECISIONS.md P3-78.

Reads a copy of the app's Room database (pulled with `adb exec-out run-as com.kinesis cat
databases/kinesis.db*` into a local folder, never into git) and writes its repetition sets in the
export format `SessionExport.readSessions` reads, so the shared core's own rules can be run over
the owner's real repetitions on the JVM (`gradlew :mmfit-eval:ownerReps`). Holds are left out.

Usage: python tools/mmfit-eval/owner_db_export.py <folder with kinesis.db> <out.export.json>
"""
import json
import math
import os
import shutil
import sqlite3
import struct
import sys
import tempfile


def doubles(blob):
    values = struct.unpack('<%dd' % (len(blob) // 8), blob)
    return [None if math.isnan(v) else v for v in values]


def pair(left, right):
    def clean(v):
        return None if v is None or (isinstance(v, float) and math.isnan(v)) else v
    return {'left': clean(left), 'right': clean(right)}


def form(text):
    """`id=left,right;id=left,right` as stored by Room's converter; `NaN` for an unmeasured side."""
    out = {}
    if not text:
        return out
    for part in text.split(';'):
        if not part.strip():
            continue
        key, values = part.split('=', 1)
        left, right = values.split(',')
        out[key] = pair(float(left), float(right))
    return out


def main(folder, out_path):
    work = tempfile.mkdtemp()
    for name in os.listdir(folder):
        if name.startswith('kinesis.db'):
            shutil.copy(os.path.join(folder, name), os.path.join(work, name))
    db = sqlite3.connect(os.path.join(work, 'kinesis.db'))
    sessions = []
    for session_id, started, offset in db.execute(
            'select sessionId, startedAtEpochMs, utcOffsetMinutes from sessions order by startedAtEpochMs'):
        sets = []
        for set_id, exercise, set_started, kind, order in db.execute(
                'select setId, exerciseId, startedAtEpochMs, kind, orderInSession from sets '
                'where sessionId = ? order by orderInSession', (session_id,)):
            if kind != 'REPETITION':
                continue
            reps = []
            for row in db.execute(
                    'select repIndex, attributedSide, startMs, endMs, validity, descentMs, ascentMs, '
                    'resampledLeft, resampledRight, rom_left, rom_right, peak_left, peak_right, '
                    'trough_left, trough_right, form from reps where setId = ? order by repIndex', (set_id,)):
                (index, side, start, end, validity, descent, ascent, res_l, res_r,
                 rom_l, rom_r, peak_l, peak_r, trough_l, trough_r, form_text) = row
                reps.append({
                    'repIndex': index,
                    'attributedSide': side,
                    'startMs': start,
                    'endMs': end,
                    'validity': validity,
                    'rangeOfMotion': pair(rom_l, rom_r),
                    'peakValue': pair(peak_l, peak_r),
                    'troughValue': pair(trough_l, trough_r),
                    'descentMs': descent,
                    'ascentMs': ascent,
                    'resampledLeft': doubles(res_l),
                    'resampledRight': doubles(res_r),
                    'form': form(form_text),
                })
            sets.append({
                'setIndex': order + 1,
                'exerciseId': exercise,
                'mode': 'repetition',
                'startedAtEpochMs': set_started,
                'recordCount': len(reps),
                'validCount': sum(1 for r in reps if r['validity'] == 'VALID'),
                'reps': reps,
                'dbSetId': set_id,
            })
        sessions.append({'sessionId': session_id, 'startedAtEpochMs': started,
                         'utcOffsetMinutes': offset, 'sets': sets})
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump({'format': 'kinesis-export', 'formatVersion': 2, 'exportedAtEpochMs': 0,
                   'sessions': sessions, 'baselines': []}, f)
    print('wrote', out_path, sum(len(s['sets']) for s in sessions), 'repetition sets')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
