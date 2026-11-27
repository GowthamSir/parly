const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function makeUserPayload(uid, overrides = {}) {
  const now = pastDate(10000);
  return {
    userId: uid,
    displayName: "Aarav",
    age: 25,
    birthYear: 2001,
    city: "Mumbai",
    state: "Maharashtra",
    bio: "Coffee, indie cinema, and rooftop conversations.",
    relationshipPreference: "Long-term",
    interests: ["Cinema", "Coffee", "Design"],
    hobbies: ["Photography", "Cooking"],
    languages: ["English", "Hindi", "Marathi"],
    prompts: [
      {
        question: "My ideal Sunday in the city",
        answer: "Filter coffee in Bandra followed by a sunset walk.",
      },
    ],
    photos: [],
    hidden: false,
    incognito: false,
    adultVerified: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function makePrivateAccountPayload(uid, overrides = {}) {
  const now = pastDate(10000);
  return {
    userId: uid,
    email: `${uid}@example.com`,
    dobIso: "2001-05-14",
    minAgeFilter: 21,
    maxAgeFilter: 35,
    distanceTier: "Same State",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID)));
  await assertFails(unauth.collection("swipes").get());
  await assertFails(unauth.collection("matches").get());
  await assertFails(unauth.collection("blocks").get());
  await assertFails(unauth.collection("reports").get());
});

test("User profile: 18+ verification, partial update, shadow field & ID poison guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid create succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID))
  );

  // Underage age (< 18) or birthYear fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(
      makeUserPayload(BOB_UID, { age: 17, birthYear: 2009 })
    )
  );

  // Ghost / shadow field fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...makeUserPayload(BOB_UID),
      isVerified: true,
    })
  );

  // Redundant @DocumentId `id` key fails
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      ...makeUserPayload(BOB_UID),
      id: BOB_UID,
    })
  );

  // Valid partial update by owner succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated bio for PAIRLY.",
      incognito: true,
      updatedAt: pastDate(1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Cross-user update fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
});

test("Discovery list query enforces hidden, incognito, and adultVerified server-side", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID));
    await db.collection("users").doc(BOB_UID).set(makeUserPayload(BOB_UID, { displayName: "Kabir" }));
    await db.collection("users").doc(CHARLIE_UID).set(
      makeUserPayload(CHARLIE_UID, { displayName: "HiddenUser", hidden: true })
    );
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Unfiltered list query MUST fail
  await assertFails(aliceDb.collection("users").get());

  // Discovery-aligned query succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .where("hidden", "==", false)
      .where("incognito", "==", false)
      .where("adultVerified", "==", true)
      .get()
  );

  // Direct get on hidden user by non-owner fails
  await assertFails(aliceDb.collection("users").doc(CHARLIE_UID).get());
});

test("Private PII account doc is strictly owner-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .set(makePrivateAccountPayload(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get()
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").get()
  );
});

test("Swipes and mutual match creation enforce reciprocal likes and prevent forging", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID));
    await db.collection("users").doc(BOB_UID).set(makeUserPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(2000);

  // Alice likes Bob
  await assertSucceeds(
    aliceDb.collection("swipes").doc(`${ALICE_UID}_${BOB_UID}`).set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      action: "like",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot forge a match before Bob likes Alice
  await assertFails(
    aliceDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).set({
      memberUids: [ALICE_UID, BOB_UID],
      status: "active",
      lastMessageText: "",
      lastSenderId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob likes Alice and atomically creates mutual match
  const batch = bobDb.batch();
  batch.set(bobDb.collection("swipes").doc(`${BOB_UID}_${ALICE_UID}`), {
    fromUid: BOB_UID,
    toUid: ALICE_UID,
    action: "super_like",
    createdAt: now,
    updatedAt: now,
  });
  batch.set(bobDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`), {
    memberUids: [ALICE_UID, BOB_UID],
    status: "active",
    lastMessageText: "",
    lastSenderId: "",
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Bob can query likes received
  await assertSucceeds(
    bobDb
      .collection("swipes")
      .where("toUid", "==", BOB_UID)
      .where("action", "in", ["like", "super_like"])
      .get()
  );

  // Charlie cannot read Alice & Bob's match
  await assertFails(charlieDb.collection("matches").doc(`${ALICE_UID}_${BOB_UID}`).get());
});

test("Chat messages work for active mutual matches and lock after unmatch", async () => {
  const matchId = `${ALICE_UID}_${BOB_UID}`;
  const now = pastDate(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(makeUserPayload(ALICE_UID));
    await db.collection("users").doc(BOB_UID).set(makeUserPayload(BOB_UID));
    await db.collection("matches").doc(matchId).set({
      memberUids: [ALICE_UID, BOB_UID],
      status: "active",
      lastMessageText: "",
      lastSenderId: "",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice sends a message
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").set({
      matchId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      text: "Hey Kabir, loved your Bandra coffee prompt!",
      moderationAcknowledged: true,
      readBy: [ALICE_UID],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Bob queries messages using aligned filter
  await assertSucceeds(
    bobDb
      .collection("matches")
      .doc(matchId)
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Bob marks message read
  await assertSucceeds(
    bobDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").update({
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  // Charlie cannot read, list with another UID, list unfiltered, or write messages
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("messages").get()
  );
  await assertFails(
    charlieDb
      .collection("matches")
      .doc(matchId)
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("matches").doc(matchId).collection("messages").doc("msg_2").set({
      matchId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      text: "Intruder",
      moderationAcknowledged: true,
      readBy: [CHARLIE_UID],
      createdAt: pastDate(600),
      updatedAt: pastDate(600),
    })
  );

  // Alice unmatches Bob -> terminal state locked
  await assertSucceeds(
    aliceDb.collection("matches").doc(matchId).update({
      status: "unmatched",
      updatedAt: pastDate(500),
    })
  );

  // Reactivating unmatched match fails
  await assertFails(
    aliceDb.collection("matches").doc(matchId).update({
      status: "active",
      updatedAt: pastDate(300),
    })
  );

  // Sending message in unmatched chat fails
  await assertFails(
    bobDb.collection("matches").doc(matchId).collection("messages").doc("msg_2").set({
      matchId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      text: "Can I still text?",
      moderationAcknowledged: true,
      readBy: [BOB_UID],
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
});

test("Blocks and reports enforce owner isolation and immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(2000);

  await assertSucceeds(
    aliceDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      createdAt: now,
    })
  );
  await assertFails(bobDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).get());
  await assertFails(bobDb.collection("blocks").doc(`${ALICE_UID}_${BOB_UID}`).delete());

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterUid: ALICE_UID,
      reportedUid: BOB_UID,
      reason: "Harassment",
      details: "Unwanted behavior in chat.",
      contextType: "chat",
      createdAt: now,
    })
  );
  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(
    aliceDb.collection("reports").doc("rep_1").update({ details: "Edited" })
  );
});
