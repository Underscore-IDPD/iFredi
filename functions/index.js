const {setGlobalOptions} = require("firebase-functions");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();
setGlobalOptions({maxInstances: 10});

exports.notifyNewMessage = onDocumentCreated(
    "chats/{chatId}/messages/{messageId}",
    async (event) => {
      const msg = event.data && event.data.data();
      if (!msg) return;

      const chatId = event.params.chatId;
      const db = getFirestore();

      const chatSnap = await db.doc(`chats/${chatId}`).get();
      const participants = chatSnap.get("participants") || [];
      const recipientId = participants.find((id) => id !== msg.senderId);
      if (!recipientId) return;

      const [recipientSnap, senderSnap] = await Promise.all([
        db.doc(`users/${recipientId}`).get(),
        db.doc(`users/${msg.senderId}`).get(),
      ]);

      const token = recipientSnap.get("fcmToken");
      if (!token) return;

      try {
        await getMessaging().send({
          token: token,
          data: {
            chatId: chatId,
            senderId: msg.senderId,
            senderName: senderSnap.get("displayName") || "Nuevo mensaje",
            text: msg.text || "",
          },
          android: {priority: "high"},
        });
      } catch (err) {
        if (err.code === "messaging/registration-token-not-registered") {
          // Token caducado: se limpia para no reintentar
          await db.doc(`users/${recipientId}`).update({
            fcmToken: FieldValue.delete(),
          });
        } else {
          logger.error("Error enviando push", err);
        }
      }
    },
);
