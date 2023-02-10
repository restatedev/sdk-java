package dev.restate.sdk.testing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Flow;

public class InvocationProcessor<T> implements Flow.Processor<T, T>, Flow.Publisher<T>, Flow.Subscriber<T>{

    private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);

    public InvocationProcessor(){

    }


    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {

    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {

    }

    @Override
    public void onNext(T t) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}


