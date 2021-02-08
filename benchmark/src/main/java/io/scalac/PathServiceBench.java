package io.scalac;


import io.scalac.extension.service.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
@Threads(1)
public class PathServiceBench {

    public static final int iters = 10;


    public List<String> urls = null;

    public String newUrl() {
        String uuid = UUID.randomUUID().toString();
        int num = ThreadLocalRandom.current().nextInt(100);
        StringBuilder builder = new StringBuilder();
        builder.append("/api/v1/account/").append(uuid);
        if (num % 2 == 0) {
            builder.append("/deposit/").append(num);
        } else {
            builder.append("/withdraw/").append(num);
        }
        return builder.toString();
    }


    @Setup(Level.Iteration)
    public void setUp() {
        urls = Stream.generate(this::newUrl)
                .limit(50)
                .collect(Collectors.toList());
    }


    private PathService regexOnly = OldCommonRegexPathService$.MODULE$;
    private PathService regexOptimized = CommonRegexPathService$.MODULE$;
    private PathService regexDummy = DummyCommonRegexPathService$.MODULE$;
    private PathService regexOptimizedCaches_100 = new CachingPathService(100);
    private PathService regexOptimizedCaches_10 = new CachingPathService(10);

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @OperationsPerInvocation(50)
    public void regexOnlyPathServiceTest(Blackhole blackhole) {
        for (String url : urls) {
            blackhole.consume(regexOnly.template(url));
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @OperationsPerInvocation(50)
    public void regexOptimizedOnlyPathServiceTest(Blackhole blackhole) {
        for (String url : urls) {
            blackhole.consume(regexOptimized.template(url));
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @OperationsPerInvocation(50)
    public void regexOptimizedCached_10_PathServiceTest(Blackhole blackhole) {
        for (String url : urls) {
            blackhole.consume(regexOptimizedCaches_10.template(url));
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @OperationsPerInvocation(50)
    public void regexOptimizedCached_100_PathServiceTest(Blackhole blackhole) {
        for (String url : urls) {
            blackhole.consume(regexOptimizedCaches_100.template(url));
        }
    }

    @Benchmark
    @Fork(1)
    @Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
    @OperationsPerInvocation(50)
    public void regexDummyPathServiceTest(Blackhole blackhole) {
        for (String url : urls) {
            blackhole.consume(regexDummy.template(url));
        }
    }


}
