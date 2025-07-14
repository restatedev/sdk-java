// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
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

  /** Resolve a command relationship to a command metadata. */
  public CommandMetadata resolveRelatedCommand(CommandRelationship relationship) {
    if (relationship instanceof CommandRelationship.Last) {
      return lastCommandMetadata();
    } else if (relationship instanceof CommandRelationship.Next next) {
      return new CommandMetadata(this.commandIndex + 1, next.type().toMessageType(), next.name());
    } else if (relationship instanceof CommandRelationship.Specific specific) {
      return new CommandMetadata(
          specific.commandIndex(), specific.type().toMessageType(), specific.name());
    } else {
      throw new IllegalArgumentException("Unknown command relationship type: " + relationship);
    }
  }

  /** Get the metadata for the last command. */
  public CommandMetadata lastCommandMetadata() {
    return new CommandMetadata(
        this.commandIndex,
        this.currentEntryTy,
        this.currentEntryName.isEmpty() ? null : this.currentEntryName);
  }
}
