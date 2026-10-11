"""The numbers behind DECISIONS.md P3-78's too-fast limits and other-side window, from MM-Fit.

Development subjects only (A, B, C, S19; the harness run was `-Psplit=development`). Reads the
harness's P5 exports and sets.csv, and for each exercise prints:

- the durations of VALID records in sets the movement counter got exactly right (so every VALID
  record there is one of MM-Fit's labelled repetitions): min, 1st and 5th percentile, median;
- for near-side profiles, the gap from one VALID record to the next VALID record on the *other*
  side, in those same sets - how close a real next repetition on the other side comes.

Usage: python tools/mmfit-eval/verdict_calibration.py <harness out dir>
"""
import csv
import json
import os
import sys


def percentile(values, p):
    values = sorted(values)
    if not values:
        return float('nan')
    return values[min(len(values) - 1, int(p * (len(values) - 1) + 0.5))]


def main(harness):
    primary = os.path.join(harness, 'primary')
    rows = list(csv.DictReader(open(os.path.join(primary, 'sets.csv'), encoding='utf-8')))
    exports = {}
    durations = {}
    gaps = {}
    exact_sets = {}
    for row in rows:
        if row['status'] != 'evaluated':
            continue
        workout = row['workout']
        if workout not in exports:
            doc = json.load(open(os.path.join(primary, workout + '.export.json'), encoding='utf-8'))
            exports[workout] = doc['sessions'][0]['sets']
        export_set = exports[workout][int(row['exportSetIndex']) - 1]
        reps = export_set['reps']
        valid = [r for r in reps if r['validity'] == 'VALID']
        exercise = row['profileId']
        if len(valid) != int(row['labelledReps']):
            continue
        exact_sets[exercise] = exact_sets.get(exercise, 0) + 1
        durations.setdefault(exercise, []).extend(r['endMs'] - r['startMs'] for r in valid)

        def side(r):
            rom = r['rangeOfMotion']
            if r.get('attributedSide'):
                return r['attributedSide']
            if rom['left'] is not None and rom['right'] is None:
                return 'LEFT'
            if rom['right'] is not None and rom['left'] is None:
                return 'RIGHT'
            return None
        ordered = sorted(valid, key=lambda r: r['startMs'])
        for a, b in zip(ordered, ordered[1:]):
            sa, sb = side(a), side(b)
            if sa and sb and sa != sb and not a.get('attributedSide'):
                gaps.setdefault(exercise, []).append(b['startMs'] - a['endMs'])

    print('VALID record durations (ms) in sets counted exactly right, development subjects')
    for exercise in sorted(durations):
        d = durations[exercise]
        print('  %-15s sets %3d  reps %4d  min %5.0f  p1 %5.0f  p5 %5.0f  median %5.0f' % (
            exercise, exact_sets[exercise], len(d), min(d), percentile(d, 0.01), percentile(d, 0.05), percentile(d, 0.5)))
    print('\nGap (ms) from a VALID record to the next VALID record on the other side, same sets (near-side profiles)')
    for exercise in sorted(gaps):
        g = gaps[exercise]
        print('  %-15s pairs %4d  min %6.0f  p5 %6.0f  median %6.0f  under 1500: %d' % (
            exercise, len(g), min(g), percentile(g, 0.05), percentile(g, 0.5), sum(1 for x in g if x < 1500)))


if __name__ == '__main__':
    main(sys.argv[1])
