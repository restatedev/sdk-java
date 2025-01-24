package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;

class Journal {
    private int commandIndex;
    private int notificationIndex;
    private int completionIndex;
    private int signalIndex;
    private MessageType currentEntryTy;
    private String currentEntryName;

    Journal() {
        this.commandIndex = -1;
        this.notificationIndex = -1;
        // Clever trick for protobuf here
        this.completionIndex = 1;
        // 1 to 16 are reserved!
        this.signalIndex = 17;
        this.currentEntryTy = MessageType.StartMessage;
        this.currentEntryName = "";
    }

    public void commandTransition(String entryName, MessageLite expected) {
        this.commandIndex++;
        this.currentEntryName = entryName;
        this.currentEntryTy = MessageType.fromMessage(expected);
    }

    public void notificationTransition(MessageLite expected) {
        this.notificationIndex++;
        this.currentEntryName = "";
        this.currentEntryTy = null;
    }

    public int getCommandIndex() {
        return this.commandIndex;
    }

    public MessageType getCurrentEntryTy() {
        return currentEntryTy;
    }

    public String getCurrentEntryName() {
        return currentEntryName;
    }

    public int getNotificationIndex() {
        return this.notificationIndex;
    }

    public int nextCompletionNotificationId() {
        int next = this.completionIndex;
        this.completionIndex++;
        return next;
    }

    public int nextSignalNotificationId() {
        int next = this.signalIndex;
        this.signalIndex++;
        return next;
    }
}