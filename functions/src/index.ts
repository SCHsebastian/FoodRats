import { initializeApp } from "firebase-admin/app";

initializeApp();

export { onCommentCreated } from "./triggers/onCommentCreated";
export { onMealCreated } from "./triggers/onMealCreated";
export { weeklyDigest } from "./triggers/weeklyDigest";
