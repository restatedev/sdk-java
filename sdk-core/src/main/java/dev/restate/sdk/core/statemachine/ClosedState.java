package dev.restate.sdk.core.statemachine;

final class ClosedState implements State {

    @Override
    public void hitError(Throwable throwable, StateContext stateContext) {
        // Ignore, as we closed already
    }

    @Override
    public void end(StateContext stateContext) {
        // Ignore, as we closed already
    }

    @Override
    public InvocationState getInvocationState() {
        return InvocationState.CLOSED;
    }
}
