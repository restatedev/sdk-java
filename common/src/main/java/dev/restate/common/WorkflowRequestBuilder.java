// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

/** Workflow handler request builder. */
public interface WorkflowRequestBuilder<Req, Res>
    extends WorkflowRequest<Req, Res>, RequestBuilder<Req, Res> {

  @Override
  WorkflowRequest<Req, Res> build();
}
