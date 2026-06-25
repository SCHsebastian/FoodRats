import { describe, expect, it } from "vitest";
import { buildMulticastMessage } from "../src/fcm/push";

describe("buildMulticastMessage — iOS (APNs) delivery", () => {
  const msg = buildMulticastMessage(
    ["tok-a", "tok-b"],
    "New comment",
    "Sebas commented on your lasagna",
    { kind: "NewComment", key: "new_comment", mealId: "m1" },
  );

  it("targets the given tokens", () => {
    expect(msg.tokens).toEqual(["tok-a", "tok-b"]);
  });

  it("carries the localized notification block", () => {
    expect(msg.notification).toEqual({
      title: "New comment",
      body: "Sebas commented on your lasagna",
    });
  });

  it("carries the data block verbatim", () => {
    expect(msg.data).toEqual({
      kind: "NewComment",
      key: "new_comment",
      mealId: "m1",
    });
  });

  it("sets apns-push-type:alert so APNs accepts and displays the push on iOS 13+", () => {
    expect(msg.apns.headers["apns-push-type"]).toBe("alert");
  });

  it("sets apns-priority:10 for immediate delivery", () => {
    expect(msg.apns.headers["apns-priority"]).toBe("10");
  });

  it("sets aps.sound so the push is NOT silent on iOS when backgrounded", () => {
    expect(msg.apns.payload.aps.sound).toBe("default");
  });

  it("mirrors title/body into aps.alert (explicit, not relying on FCM synthesis)", () => {
    expect(msg.apns.payload.aps.alert).toEqual({
      title: "New comment",
      body: "Sebas commented on your lasagna",
    });
  });

  it("marks the Android counterpart high-priority", () => {
    expect(msg.android.priority).toBe("high");
  });
});
