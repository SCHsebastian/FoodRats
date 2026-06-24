import { assertFails, assertSucceeds, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { makeEnv, mealId } from "./helpers";
let env: RulesTestEnvironment;
const CREW="c1", DAY="2026-06-13", ID=mealId(CREW,"alice",DAY,"lunch"), PATH=`crews/${CREW}/meals/${ID}`;
const base=(o:any={})=>({id:ID,authorId:"alice",crewId:CREW,dayKey:DAY,slot:"lunch",platePath:"x",publishedAtEpochMs:Date.now(),ratings:{},ratingSum:0,voterCount:0,...o});
beforeAll(async()=>{env=await makeEnv();});
afterAll(async()=>{await env.cleanup();});
async function seed(m:any){await env.clearFirestore();await env.withSecurityRulesDisabled(async(ctx)=>{await setDoc(doc(ctx.firestore(),"crews/c1"),{ownerId:"alice",name:"C",memberIds:["alice","bob"],members:{}});await setDoc(doc(ctx.firestore(),PATH),m);});}

describe("probe which deny-path THROWS (evaluation error)", () => {
  // self-vote: alice writes ratings:{alice:...}. uid=alice. L292 (uid != authorId) is FALSE → short circuits, should be 'false' not 'error'
  it("self-vote alice", async () => {
    await seed(base());
    const db=env.authenticatedContext("alice").firestore();
    await assertFails(updateDoc(doc(db,PATH),{ratings:{alice:{score:5}},ratingSum:5,voterCount:1}));
  });
  // bob writes but touches a DIFFERENT user's key only (changes carol, not bob) → ratings[uid=bob] absent → L297 access throws
  it("bob writes carol's entry (ratings[bob] absent → L297 throws)", async () => {
    await seed(base());
    const db=env.authenticatedContext("bob").firestore();
    await assertFails(updateDoc(doc(db,PATH),{ratings:{carol:{score:5}},ratingSum:5,voterCount:1}));
  });
});
