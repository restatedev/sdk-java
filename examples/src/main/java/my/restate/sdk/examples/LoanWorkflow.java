// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package my.restate.sdk.examples;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.annotation.ServiceType;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import dev.restate.sdk.workflow.DurablePromiseKey;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowSharedContext;
import dev.restate.sdk.workflow.generated.WorkflowExecutionState;
import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import my.restate.sdk.examples.generated.bank.BankRestate;
import my.restate.sdk.examples.generated.bank.TransferRequest;
import my.restate.sdk.examples.generated.bank.TransferResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service(ServiceType.WORKFLOW)
public class LoanWorkflow {

  // --- Data types used by the Loan Worfklow

  enum Status {
    SUBMITTED,
    WAITING_HUMAN_APPROVAL,
    APPROVED,
    NOT_APPROVED,
    TRANSFER_SUCCEEDED,
    TRANSFER_FAILED
  }

  public static class LoanRequest {

    private final String customerName;
    private final String customerId;
    private final String customerBankAccount;
    private final BigDecimal amount;

    @JsonCreator
    public LoanRequest(
        @JsonProperty("customerName") String customerName,
        @JsonProperty("customerId") String customerId,
        @JsonProperty("customerBankAccount") String customerBankAccount,
        @JsonProperty("amount") BigDecimal amount) {
      this.customerName = customerName;
      this.customerId = customerId;
      this.customerBankAccount = customerBankAccount;
      this.amount = amount;
    }

    public String getCustomerName() {
      return customerName;
    }

    public String getCustomerId() {
      return customerId;
    }

    public String getCustomerBankAccount() {
      return customerBankAccount;
    }

    public BigDecimal getAmount() {
      return amount;
    }
  }

  private static final Logger LOG = LogManager.getLogger(LoanWorkflow.class);

  private static final StateKey<Status> STATUS =
      StateKey.of("status", JacksonSerdes.of(Status.class));
  private static final StateKey<LoanRequest> LOAN_REQUEST =
      StateKey.of("loanRequest", JacksonSerdes.of(LoanRequest.class));
  private static final DurablePromiseKey<Boolean> HUMAN_APPROVAL =
      DurablePromiseKey.of("humanApproval", CoreSerdes.JSON_BOOLEAN);
  private static final StateKey<String> TRANSFER_EXECUTION_TIME =
      StateKey.of("transferExecutionTime", CoreSerdes.JSON_STRING);

  // --- The main workflow method

  @Workflow
  public void run(WorkflowContext ctx, LoanRequest loanRequest) {
    // 1. Set status
    ctx.set(STATUS, Status.SUBMITTED);
    ctx.set(LOAN_REQUEST, loanRequest);

    LOG.info("Loan request submitted");

    // 2. Ask human approval
    ctx.sideEffect(() -> askHumanApproval(ctx.workflowKey()));
    ctx.set(STATUS, Status.WAITING_HUMAN_APPROVAL);

    // 3. Wait human approval
    boolean approved = ctx.durablePromise(HUMAN_APPROVAL).awaitable().await();
    if (!approved) {
      LOG.info("Not approved");
      ctx.set(STATUS, Status.NOT_APPROVED);
      return;
    }
    LOG.info("Approved");
    ctx.set(STATUS, Status.APPROVED);

    // 4. Request money transaction to the bank
    var bankClient = BankRestate.newClient(ctx);
    TransferResult transferResponse;
    try {
      transferResponse =
          bankClient
              .transfer(
                  TransferRequest.newBuilder()
                      .setAmount(loanRequest.getAmount().toString())
                      .setBankAccount(loanRequest.getCustomerBankAccount())
                      .build())
              .await(Duration.ofDays(7));
    } catch (TerminalException | TimeoutException e) {
      LOG.warn("Transaction failed", e);
      ctx.set(STATUS, Status.TRANSFER_FAILED);
      return;
    }

    LOG.info("Transfer complete");

    // 5. Transfer complete!
    ctx.set(TRANSFER_EXECUTION_TIME, transferResponse.getExecutionTime());
    ctx.set(STATUS, Status.TRANSFER_SUCCEEDED);
  }

  // --- Methods to approve/reject loan

  @Shared
  public void approveLoan(WorkflowSharedContext ctx) {
    ctx.durablePromiseHandle(HUMAN_APPROVAL).resolve(true);
  }

  @Shared
  public void rejectLoan(WorkflowSharedContext ctx) {
    ctx.durablePromiseHandle(HUMAN_APPROVAL).resolve(false);
  }

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder()
        .with(new LoanWorkflow())
        .withService(new MockBank())
        .buildAndListen();

    // Register the service in the meantime!
    LOG.info("Now it's time to register this deployment");

    try {
      Thread.sleep(20_000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // To invoke the workflow:
    Channel restateChannel =
        NettyChannelBuilder.forAddress("127.0.0.1", 8080).usePlaintext().build();
    LoanWorkflowExternalClient client = new LoanWorkflowExternalClient(restateChannel, "my-loan");

    WorkflowExecutionState state =
        client.submit(
            new LoanRequest(
                "Francesco", "slinkydeveloper", "DE1234", new BigDecimal("1000000000")));
    if (state != WorkflowExecutionState.STARTED) {
      throw new IllegalStateException("Unexpected state " + state);
    }

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

    while (!client.isCompleted()) {
      LOG.info("Not completed yet");
      try {
        Thread.sleep(10_000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    LOG.info("Loan workflow completed");
  }

  // -- Some mocks

  private static void askHumanApproval(String workflowKey) throws InterruptedException {
    LOG.info("Sending human approval request");
    Thread.sleep(1000);
  }

  private static class MockBank extends BankRestate.BankRestateImplBase {
    @Override
    public TransferResult transfer(Context context, TransferRequest request)
        throws TerminalException {
      boolean shouldAccept = context.random().nextInt(3) != 1;
      if (shouldAccept) {
        return TransferResult.newBuilder().setExecutionTime(Instant.now().toString()).build();
      } else {
        throw new TerminalException("Won't accept the transfer");
      }
    }
  }
}
