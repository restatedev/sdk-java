// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import dev.restate.sdk.Context;
import dev.restate.sdk.SharedWorkflowContext;
import dev.restate.sdk.WorkflowContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Workflow
public class LoanWorkflow {

  // --- Data types used by the Loan Workflow

  public enum Status {
    UNKNOWN,
    SUBMITTED,
    WAITING_HUMAN_APPROVAL,
    APPROVED,
    NOT_APPROVED,
    TRANSFER_SUCCEEDED,
    TRANSFER_FAILED
  }

  public record LoanRequest(
      String customerName, String customerId, String customerBankAccount, BigDecimal amount) {}

  private static final Logger LOG = LogManager.getLogger(LoanWorkflow.class);

  private static final StateKey<Status> STATUS = StateKey.of("status", Status.class);
  private static final StateKey<LoanRequest> LOAN_REQUEST =
      StateKey.of("loanRequest", LoanRequest.class);
  private static final DurablePromiseKey<Boolean> HUMAN_APPROVAL =
      DurablePromiseKey.of("humanApproval", Boolean.class);
  private static final StateKey<String> TRANSFER_EXECUTION_TIME =
      StateKey.of("transferExecutionTime", String.class);

  // --- The main workflow method

  @Workflow
  public String run(WorkflowContext ctx, LoanRequest loanRequest) {
    // 1. Set status
    ctx.set(STATUS, Status.SUBMITTED);
    ctx.set(LOAN_REQUEST, loanRequest);

    LOG.info("Loan request submitted");

    // 2. Ask human approval
    ctx.run(() -> askHumanApproval(ctx.key()));
    ctx.set(STATUS, Status.WAITING_HUMAN_APPROVAL);

    // 3. Wait human approval
    boolean approved = ctx.promise(HUMAN_APPROVAL).future().await();
    if (!approved) {
      LOG.info("Not approved");
      ctx.set(STATUS, Status.NOT_APPROVED);
      return "Not approved";
    }
    LOG.info("Approved");
    ctx.set(STATUS, Status.APPROVED);

    // 4. Request money transaction to the bank
    var bankClient = LoanWorkflowMockBankClient.fromContext(ctx);
    Instant executionTime;
    try {
      executionTime =
          bankClient
              .transfer(
                  new TransferRequest(loanRequest.customerBankAccount(), loanRequest.amount()))
              .await(Duration.ofDays(7));
    } catch (TerminalException e) {
      LOG.warn("Transaction failed", e);
      ctx.set(STATUS, Status.TRANSFER_FAILED);
      return "Failed";
    }

    LOG.info("Transfer complete");

    // 5. Transfer complete!
    ctx.set(TRANSFER_EXECUTION_TIME, executionTime.toString());
    ctx.set(STATUS, Status.TRANSFER_SUCCEEDED);

    return "Transfer succeeded";
  }

  // --- Methods to approve/reject loan

  @Shared
  public String approveLoan(SharedWorkflowContext ctx) {
    ctx.promiseHandle(HUMAN_APPROVAL).resolve(true);
    return "Approved";
  }

  @Shared
  public void rejectLoan(SharedWorkflowContext ctx) {
    ctx.promiseHandle(HUMAN_APPROVAL).resolve(false);
  }

  @Shared
  public Status getStatus(SharedWorkflowContext ctx) {
    return ctx.get(STATUS).orElse(Status.UNKNOWN);
  }

  public static void main(String[] args) {
    Endpoint endpoint = Endpoint.builder().bind(new LoanWorkflow()).bind(new MockBank()).build();

    RestateHttpServer.listen(endpoint);

    // Register the service in the meantime!
    LOG.info("Now it's time to register this deployment");

    try {
      Thread.sleep(20_000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // To invoke the workflow:
    LoanWorkflowClient.IngressClient client =
        LoanWorkflowClient.connect("http://127.0.0.1:8080", "my-loan");

    var state =
        client.submit(
            new LoanRequest(
                "Francesco", "slinkydeveloper", "DE1234", new BigDecimal("1000000000")));

    LOG.info("Started loan workflow");

    // Takes some bureaucratic time to approve the loan
    try {
      Thread.sleep(10_000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    LOG.info("We took the decision to approve your loan! You can now achieve your dreams!");

    // Now approve it
    client.approveLoan();

    // Wait for output
    client.workflowHandle().attach();

    LOG.info("Loan workflow completed, now in status {}", client.getStatus());
  }

  // -- Some mocks

  private static void askHumanApproval(String workflowKey) throws InterruptedException {
    LOG.info("Sending human approval request for workflow {}", workflowKey);
    Thread.sleep(1000);
  }

  @Service
  static class MockBank {
    @Handler
    public Instant transfer(Context context, TransferRequest request) throws TerminalException {
      boolean shouldAccept = context.random().nextInt(3) != 1;
      if (shouldAccept) {
        return Instant.now();
      } else {
        throw new TerminalException("Won't accept the transfer");
      }
    }
  }

  public record TransferRequest(String bankAccount, BigDecimal amount) {}
}
