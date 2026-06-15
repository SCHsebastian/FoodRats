import { initializeApp } from "firebase-admin/app";

initializeApp();

export { onCommentCreated } from "./triggers/onCommentCreated";
export { onMealCreated } from "./triggers/onMealCreated";
export { onMealDeleted } from "./triggers/onMealDeleted";
export { onPlateImageFinalized } from "./triggers/onPlateImageFinalized";
export { weeklyDigest } from "./triggers/weeklyDigest";
export { streakNudge } from "./triggers/streakNudge";
export { mintPlateUrls } from "./callables/mintPlateUrls";
export { deleteAccount } from "./callables/deleteAccount";
export { exportMyData } from "./callables/exportMyData";
