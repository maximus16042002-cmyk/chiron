// Unit tests for firestore.rules on the Firebase Local Emulator - DECISIONS.md P3-19.
//
//   cd tools/firestore-rules && npm install && npm test
//
// Runs against a `demo-` project, which the emulator serves entirely offline:
// nothing here can reach the real kinesis-5b97b project. Dev-only; nothing in
// this folder ships in the app.
//
// The requirement, as the owner stated it: a signed-in user can read and add
// only their own sessions and can never edit or delete them; a signed-out user
// can do nothing.

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kinesis',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

/** A document shaped as SessionDocument.toMap writes one. */
function session(id, overrides = {}) {
  return {
    sessionId: id,
    startedAtEpochMs: 1790274862897.5,
    utcOffsetMinutes: 330,
    schema: 1,
    sets: [
      {
        exerciseId: 'squat',
        startedAtEpochMs: 1790274862897.5,
        kind: 'REPETITION',
        reps: [{ repIndex: 1, validity: 'VALID', startMs: 0, endMs: 2000 }],
        holds: [],
      },
    ],
    ...overrides,
  };
}

const path = (uid, id) => `users/${uid}/sessions/${id}`;

/** Seeds a document with the rules switched off, as an existing upload. */
async function seed(uid, id, data = session(id)) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path(uid, id)), data);
  });
}

describe('signed out', () => {
  test('cannot read a session', async () => {
    await seed('alice', 's1');
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, path('alice', 's1'))));
  });

  test('cannot list sessions', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDocs(collection(db, 'users/alice/sessions')));
  });

  test('cannot add a session', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(db, path('alice', 's1')), session('s1')));
  });
});

describe('signed in', () => {
  test('adds a session of their own', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await assertSucceeds(setDoc(doc(db, path('alice', 's1')), session('s1')));
  });

  test('reads and lists their own sessions', async () => {
    await seed('alice', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertSucceeds(getDoc(doc(db, path('alice', 's1'))));
    await assertSucceeds(getDocs(collection(db, 'users/alice/sessions')));
  });

  test("cannot read or list another user's sessions", async () => {
    await seed('bob', 's9');
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(getDoc(doc(db, path('bob', 's9'))));
    await assertFails(getDocs(collection(db, 'users/bob/sessions')));
  });

  test("cannot add a session to another user's account", async () => {
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(setDoc(doc(db, path('bob', 's1')), session('s1')));
  });

  test('cannot edit a session it recorded', async () => {
    await seed('alice', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(updateDoc(doc(db, path('alice', 's1')), { utcOffsetMinutes: 0 }));
    await assertFails(setDoc(doc(db, path('alice', 's1')), session('s1', { sets: [] })));
  });

  test('cannot delete a session it recorded', async () => {
    await seed('alice', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(deleteDoc(doc(db, path('alice', 's1'))));
  });

  test('cannot add a malformed session', async () => {
    const db = env.authenticatedContext('alice').firestore();
    const bad = [
      ['a different sessionId from its path', session('other')],
      ['no sets', (({ sets, ...rest }) => rest)(session('s1'))],
      ['a string offset', session('s1', { utcOffsetMinutes: '330' })],
      ['a non-numeric start', session('s1', { startedAtEpochMs: 'yesterday' })],
      ['an empty sessionId', session('', {})],
    ];
    for (const [what, data] of bad) {
      const id = what === 'an empty sessionId' ? 'blank' : 's1';
      await assertFails(setDoc(doc(db, path('alice', id)), data), what);
    }
  });

  test('cannot touch anything outside its sessions', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(setDoc(doc(db, 'users/alice'), { name: 'alice' }));
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(setDoc(doc(db, 'anything/else'), { x: 1 }));
  });
});

// The app uploads with set() on a fixed document id, and SessionSyncWorker
// retries every failed upload. An upload the server committed but whose
// acknowledgement was lost is retried as a second, identical set(), which the
// rules see as an update of an existing document.
describe('a retried upload', () => {
  test('of the same, unchanged session succeeds - it edits nothing', async () => {
    await seed('alice', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertSucceeds(setDoc(doc(db, path('alice', 's1')), session('s1')));
  });

  test('that differs in any way is an edit, and refused', async () => {
    await seed('alice', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(setDoc(doc(db, path('alice', 's1')), session('s1', { schema: 2 })));
  });

  test("of another user's session is refused even when identical", async () => {
    await seed('bob', 's1');
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(setDoc(doc(db, path('bob', 's1')), session('s1')));
  });
});
